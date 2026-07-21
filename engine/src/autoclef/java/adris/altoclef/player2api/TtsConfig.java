package adris.altoclef.player2api;

/**
 * Voice-output configuration for the AI Companion fork.
 *
 * <p>Upstream voiced the companion through the Player2 cloud ({@code api.player2.game}). We keep the
 * (good) upstream shape — the server sends the text to the owner's client and the <em>client</em>
 * fetches and plays the audio, so sound comes out of the player's speakers rather than the server's —
 * and repoint the audio source at a local, OpenAI-compatible <b>Kokoro</b> endpoint. See {@code tts/}
 * for the docker-compose stack that serves it.
 *
 * <p>These values are read server-side and pushed to the client in the {@code playerengine:stream_tts}
 * packet, so the client needs no config of its own. Overridable via system properties or environment
 * variables, and fed from the consumer mod's JSON config ({@code tts.*}):
 * <ul>
 *   <li>{@code -Daicompanion.tts.enabled=true} or {@code AICOMPANION_TTS_ENABLED}</li>
 *   <li>{@code -Daicompanion.tts.endpoint=...} or {@code AICOMPANION_TTS_ENDPOINT}</li>
 * </ul>
 */
public final class TtsConfig {
    private TtsConfig() {}

    /**
     * Master switch. Default {@code false} because voice needs the Kokoro stack running — enabling it
     * by default would make every companion line attempt a doomed HTTP call on a machine that never
     * started the container.
     */
    public static volatile boolean enabled =
            Boolean.parseBoolean(resolve("aicompanion.tts.enabled", "AICOMPANION_TTS_ENABLED", "false"));

    /** Base URL of the Kokoro server. {@code /v1/audio/speech} is appended. Must be reachable from the CLIENT. */
    public static volatile String endpoint =
            resolve("aicompanion.tts.endpoint", "AICOMPANION_TTS_ENDPOINT", "http://localhost:8880");

    /** Sent as {@code "model"}. Kokoro-FastAPI serves whatever it has loaded; the field is for OpenAI compatibility. */
    public static volatile String model =
            resolve("aicompanion.tts.model", "AICOMPANION_TTS_MODEL", "kokoro");

    /**
     * Kokoro voice id (e.g. {@code af_heart}, {@code am_michael}). {@code GET /v1/audio/voices} lists them.
     * A non-blank {@code Character.voiceIds()[0]} overrides this, so a persona can carry its own voice.
     */
    public static volatile String voice =
            resolve("aicompanion.tts.voice", "AICOMPANION_TTS_VOICE", "af_heart");

    /** Playback/synthesis rate passed to Kokoro. 1.0 = natural. */
    public static volatile double speed =
            Double.parseDouble(resolve("aicompanion.tts.speed", "AICOMPANION_TTS_SPEED", "1.0"));

    /** Base URL without a trailing slash, so path concatenation cannot produce a double slash. */
    public static String normalizedEndpoint() {
        String e = endpoint == null ? "" : endpoint.trim();
        while (e.endsWith("/")) {
            e = e.substring(0, e.length() - 1);
        }
        return e;
    }

    private static String resolve(String property, String env, String fallback) {
        String v = System.getProperty(property);
        if (v == null || v.isBlank()) {
            v = System.getenv(env);
        }
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
