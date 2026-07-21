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

package adris.altoclef.player2api.utils;

import com.google.gson.JsonObject;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Client-side audio playback for companion speech.
 *
 * <p>Runs on the player's machine (driven by the {@code playerengine:stream_tts} packet), so the
 * audio comes out of the player's speakers, not the server's. The endpoint/voice/speed are supplied
 * by the server in that packet rather than read from config here.
 */
public class AudioUtils {

    /**
     * Fetch speech for {@code text} from a Kokoro (OpenAI-compatible) endpoint and play it.
     *
     * @param endpoint base URL, no trailing slash; {@code /v1/audio/speech} is appended
     */
    public static void streamAudio(String endpoint, String model, String voice, String text, double speed) {
        HttpURLConnection connection = null;
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("input", text);
            requestBody.addProperty("voice", voice);
            // Must be wav: javax.sound.sampled ships no MP3 decoder, so any other format throws
            // UnsupportedAudioFileException. Kokoro returns 16-bit mono PCM @24kHz, which it reads natively.
            requestBody.addProperty("response_format", "wav");
            requestBody.addProperty("speed", speed);

            URL url = new URL(endpoint + "/v1/audio/speech");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "audio/wav");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(30000);

            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            try (InputStream inputStream = connection.getInputStream();
                 AudioInputStream audioStream = AudioSystem.getAudioInputStream(new BufferedInputStream(inputStream))) {

                AudioFormat format = audioStream.getFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

                try (SourceDataLine sourceDataLine = (SourceDataLine) AudioSystem.getLine(info)) {
                    sourceDataLine.open(format);
                    sourceDataLine.start();

                    byte[] buffer = new byte[4096];
                    int bytesRead = 0;
                    while ((bytesRead = audioStream.read(buffer)) != -1) {
                        sourceDataLine.write(buffer, 0, bytesRead);
                    }

                    sourceDataLine.drain();
                    sourceDataLine.stop();
                }
            }
        } catch (Exception e) {
            // Never let a TTS failure disturb the game: the line is already in chat either way.
            System.err.println("[AudioUtils] TTS playback failed (" + endpoint
                    + "): " + e + " — is the Kokoro stack running? See tts/README.md");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
