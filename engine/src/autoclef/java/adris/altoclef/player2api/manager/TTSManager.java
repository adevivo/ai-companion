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
    private static boolean TTSLocked = false;
    private static long estimatedEndTime = 0;
    private static final ExecutorService ttsThread = Executors.newSingleThreadExecutor();

    private static void setEstimatedEndTime(String message) {
        int waitTimeSec = (int) Math.ceil(message.length() / (double) TTScharactersPerSecond) + 1;

        LOGGER.info("TTSManager/ waiting time={} (sec) for message={}", waitTimeSec, message);

        long waitNanos = TimeUnit.SECONDS.toNanos(waitTimeSec);
        estimatedEndTime = System.nanoTime() + waitNanos;
    }

    public static void TTS(String message, Character character, Player2APIService player2apiService) {
        // Voice is opt-in and needs the local Kokoro stack up (see tts/README.md). Off => stay silent
        // and, importantly, never take the lock below.
        if (!adris.altoclef.player2api.TtsConfig.enabled) {
            return;
        }
        TTSLocked = true;
        LOGGER.info("Locking TTS based on msg={}", message);
        estimatedEndTime = Long.MAX_VALUE;

        ttsThread.submit(() -> {
            try {
                player2apiService.textToSpeech(message, character, (_unusedMap) -> {});
            } finally {
                // Always arm the release timer. estimatedEndTime is MAX_VALUE until this runs, so if the
                // dispatch throws, skipping it would hold the conversation lock (and mute the companion)
                // for the rest of the session.
                setEstimatedEndTime(message);
            }
        });
    }

    public static boolean isLocked() {
        return TTSLocked;
    }

    public static void injectOnTick(MinecraftServer server) {
        // release lock if we think we have finished.
        server.execute(() -> {
            if ((System.nanoTime() > estimatedEndTime) && TTSLocked) {
                LOGGER.info("TTS releasing lock");
                TTSLocked = false;
            }
        });
    }
}