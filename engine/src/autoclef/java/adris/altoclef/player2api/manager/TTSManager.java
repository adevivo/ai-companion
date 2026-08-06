/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package adris.altoclef.player2api.manager;


import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import adris.altoclef.player2api.Character;
import adris.altoclef.player2api.Player2APIService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public class TTSManager {
    private static final Logger LOGGER = LogManager.getLogger();

    /** Server → client: speak this line. */
    public static final ResourceLocation SPEAK_CHANNEL = new ResourceLocation("playerengine", "stream_tts");

    /** Client → server: that line finished, or could not be played at all. */
    public static final ResourceLocation ACK_CHANNEL = new ResourceLocation("playerengine", "tts_done");

    /**
     * When each speaker is expected to stop talking, keyed by companion entity UUID.
     *
     * <p>Per companion rather than one global flag. The flag used to gate the whole conversation
     * system, so one companion speaking froze every other companion's thinking for the estimated
     * duration of the sentence — with several out, whoever was busiest starved the rest.
     *
     * <p>Entries are released by the client's ack rather than by a timer. What is stored is only the
     * {@link #ACK_GUARD_SECONDS} backstop for an ack that never arrives.
     */
    private static final Map<UUID, Long> speakingUntil = new ConcurrentHashMap<>();

    /**
     * Players whose client has told us it cannot play audio, and when to try them again.
     *
     * <p>Without this, a machine with no Kokoro container pays the round trip on every single line.
     * That is cheap when the endpoint refuses the connection outright, and 5 seconds of connect
     * timeout when it is a LAN address pointing at nothing.
     */
    private static final Map<UUID, Long> ttsUnavailableUntil = new ConcurrentHashMap<>();

    /**
     * How long to stop trying after a client reports it cannot speak. Long enough that a missing
     * container costs nothing, short enough that starting one is noticed without a reload.
     */
    private static final long UNAVAILABLE_RETRY_SECONDS = 300;

    /**
     * Backstop for a lost ack, so a client that disconnects mid-sentence cannot mute a companion for
     * the rest of the session — which is exactly what the old unbounded {@code MAX_VALUE} lock did
     * whenever the dispatch failed.
     *
     * <p>Generous on purpose: it is a safety net, not the mechanism. The client's own timeouts cap a
     * real round trip at roughly 35 seconds (5s connect + 30s read).
     */
    private static final long ACK_GUARD_SECONDS = 60;

    private static final ExecutorService ttsThread = Executors.newSingleThreadExecutor();

    public static void TTS(String message, Character character, Player2APIService player2apiService,
            UUID speaker) {
        // Voice is opt-in on the endpoint being reachable from the CLIENT, which is the one thing
        // this side cannot check. Off => stay silent and, importantly, never take the lock below.
        if (!adris.altoclef.player2api.TtsConfig.enabled) {
            return;
        }
        // Arm the backstop before dispatching: the ack can land before this method returns, so
        // setting it afterwards would overwrite a release with a lock.
        speakingUntil.put(speaker, System.nanoTime() + TimeUnit.SECONDS.toNanos(ACK_GUARD_SECONDS));

        ttsThread.submit(() -> {
            boolean dispatched = false;
            try {
                dispatched = player2apiService.textToSpeech(message, character, speaker);
            } finally {
                if (dispatched) {
                    // Logged because success is otherwise entirely silent: with no line here and none
                    // on the ack, "spoke perfectly" and "never tried" leave identical logs, and the
                    // only way to tell them apart is to be wearing headphones at the time.
                    LOGGER.info("TTS: asked the owner's client to speak {} chars, speaker={}",
                            message.length(), speaker);
                } else {
                    // Nothing is going to speak and nothing is going to ack, so holding the lock for
                    // the full guard would stall this companion's next turn for a minute.
                    speakingUntil.remove(speaker);
                    LOGGER.info("TTS: not sent for speaker={} — owner offline, or their client is in "
                            + "the no-audio back-off", speaker);
                }
            }
        });
    }

    /**
     * Listen for clients reporting on the lines we asked them to speak. Call once, at mod init.
     */
    public static void registerAckReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(
                ACK_CHANNEL, (server, player, handler, buf, responseSender) -> {
                    UUID speaker = buf.readUUID();
                    boolean spoken = buf.readBoolean();
                    server.execute(() -> onSpeechAck(player.getUUID(), speaker, spoken));
                });
    }

    /**
     * The owner's client reporting on a line we asked it to speak.
     *
     * <p>{@code spoken} false means the audio never played — no Kokoro server on that machine, or the
     * synthesis failed. Either way the companion is not talking, so it is released immediately and we
     * stop sending to that player for a while.
     *
     * @param listener the player whose client answered
     * @param speaker  the companion entity that was speaking
     */
    public static void onSpeechAck(UUID listener, UUID speaker, boolean spoken) {
        speakingUntil.remove(speaker);
        if (spoken) {
            LOGGER.info("TTS: speaker={} finished speaking; released", speaker);
            ttsUnavailableUntil.remove(listener);
            return;
        }
        long retryAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(UNAVAILABLE_RETRY_SECONDS);
        if (ttsUnavailableUntil.put(listener, retryAt) == null) {
            LOGGER.warn("TTS is enabled but player {} could not play it (no Kokoro server reachable "
                    + "from that machine?). Not sending speech to them for {}s. See "
                    + "config/aicompanion/tts/README.md", listener, UNAVAILABLE_RETRY_SECONDS);
        }
    }

    /** Whether this player's client has recently failed to play audio and is still in the back-off. */
    public static boolean isTtsUnavailable(UUID listener) {
        Long retryAt = ttsUnavailableUntil.get(listener);
        return retryAt != null && System.nanoTime() < retryAt;
    }

    /**
     * Forget which clients could not play audio, so a config reload retries them at once.
     *
     * <p>The whole point of the back-off is that the player is off starting a container; making them
     * wait out the retry window after telling the mod they are ready would undo it.
     */
    public static void clearUnavailable() {
        ttsUnavailableUntil.clear();
    }

    /**
     * Release every speech lock. A world that stops mid-utterance would otherwise leave entries
     * belonging to a dead session, keyed by companion UUIDs that are persisted in the save and so
     * come back identical — muting those companions in the next world.
     */
    public static void reset() {
        speakingUntil.clear();
        ttsUnavailableUntil.clear();
    }

    /** Whether this companion is still expected to be mid-sentence. */
    public static boolean isSpeaking(UUID speaker) {
        Long until = speakingUntil.get(speaker);
        return until != null && System.nanoTime() <= until;
    }

    /** Whether anybody is mid-sentence. Diagnostics only — nothing gates on it. */
    public static boolean isAnyoneSpeaking() {
        return speakingUntil.values().stream().anyMatch(until -> System.nanoTime() <= until);
    }

    public static void injectOnTick(MinecraftServer server) {
        // Drop expired entries so neither map grows with every despawned companion or departed player.
        server.execute(() -> {
            long now = System.nanoTime();
            speakingUntil.entrySet().removeIf(e -> now > e.getValue());
            ttsUnavailableUntil.entrySet().removeIf(e -> now > e.getValue());
        });
    }
}