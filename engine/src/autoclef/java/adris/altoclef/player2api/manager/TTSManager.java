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

import net.minecraft.server.MinecraftServer;

public class TTSManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int TTScharactersPerSecond = 25; // approx how fast (characters/sec) does the TTS talk
    /**
     * When each speaker is expected to stop talking, keyed by companion entity UUID.
     *
     * <p>Per companion rather than one global flag. The flag used to gate the whole conversation
     * system, so one companion speaking froze every other companion's thinking for the estimated
     * duration of the sentence — with several out, whoever was busiest starved the rest. Speech
     * itself is still serialized by the single {@link #ttsThread}, so voices do not overlap; what is
     * no longer serialized is everyone else's reasoning.
     */
    private static final Map<UUID, Long> speakingUntil = new ConcurrentHashMap<>();

    private static final ExecutorService ttsThread = Executors.newSingleThreadExecutor();

    private static void setEstimatedEndTime(UUID speaker, String message) {
        int waitTimeSec = (int) Math.ceil(message.length() / (double) TTScharactersPerSecond) + 1;

        LOGGER.info("TTSManager/ waiting time={} (sec) for message={}", waitTimeSec, message);

        long waitNanos = TimeUnit.SECONDS.toNanos(waitTimeSec);
        speakingUntil.put(speaker, System.nanoTime() + waitNanos);
    }

    public static void TTS(String message, Character character, Player2APIService player2apiService,
            UUID speaker) {
        // Voice is opt-in and needs the local Kokoro stack up (see tts/README.md). Off => stay silent
        // and, importantly, never take the lock below.
        if (!adris.altoclef.player2api.TtsConfig.enabled) {
            return;
        }
        LOGGER.info("Locking TTS for speaker={} based on msg={}", speaker, message);
        // Held open until the dispatch below works out the real duration, so a turn cannot be taken
        // in the gap between claiming the voice and knowing how long the sentence runs.
        speakingUntil.put(speaker, Long.MAX_VALUE);

        ttsThread.submit(() -> {
            try {
                player2apiService.textToSpeech(message, character, (_unusedMap) -> {});
            } finally {
                // Always arm the release timer. The entry is MAX_VALUE until this runs, so if the
                // dispatch throws, skipping it would keep this companion silent for the rest of the
                // session.
                setEstimatedEndTime(speaker, message);
            }
        });
    }

    /**
     * Release every speech lock. A world that stops mid-utterance would otherwise leave entries
     * belonging to a dead session, keyed by companion UUIDs that are persisted in the save and so
     * come back identical — muting those companions in the next world.
     */
    public static void reset() {
        speakingUntil.clear();
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
        // Drop finished speakers so the map does not grow with every despawned companion.
        server.execute(() -> speakingUntil.entrySet().removeIf(e -> System.nanoTime() > e.getValue()));
    }
}