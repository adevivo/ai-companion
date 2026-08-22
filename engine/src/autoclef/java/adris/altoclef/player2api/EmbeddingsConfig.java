package adris.altoclef.player2api;

/**
 * Embedding endpoint configuration — the input side of the companion's memory.
 *
 * <p><b>This is a different server from {@link LlmConfig}, and that is the whole point of this
 * class.</b> The obvious-looking shortcut — "llama.cpp already serves an OpenAI-compatible API on
 * :3030, so ask it for {@code /v1/embeddings} too" — does not work, for three separate reasons,
 * each of which is on its own sufficient:
 *
 * <ol>
 *   <li>A stock {@code llama-server} answers {@code /v1/embeddings} with
 *       {@code 501 "This server does not support embeddings. Start it with --embeddings"}. Measured
 *       against the brain endpoint on 2026-08-16.</li>
 *   <li>Even started with {@code --embeddings}, it serves <em>the model it has loaded</em>. The brain
 *       runs a chat model (Qwen2.5-14B-Instruct, {@code n_embd} 5120, {@code capabilities:
 *       ["completion"]}). Pooled hidden states from an instruct model are not a retrieval embedding,
 *       and they are the wrong width.</li>
 *   <li>llama.cpp serves one model per process. Chat and embeddings cannot both come from one
 *       instance regardless of flags.</li>
 * </ol>
 *
 * <p><b>The dimension is load-bearing.</b> Every measured number in the memory engine — int8
 * quantisation costing 4.8% of the exact ranking, candidate multiplier 2 recovering 100.00% of it,
 * k=5–10 — was taken at <b>768 dimensions with {@code nomic-embed-text}</b>. Changing the embedder
 * invalidates all of them, and the bench that could re-measure lives in the POC, not here. So the
 * default is the model those numbers came from, and {@link #expectedDimension} exists to make a
 * silent substitution loud.
 *
 * <p><b>The embedder is part of the wire format.</b> The memory engine guarantees that its Java and
 * JS implementations rank identically <em>given the same vectors</em>. Nothing guarantees two
 * clients <em>produce</em> the same vectors. If this mod embeds with one model and a browser client
 * embeds with another — or the same model at a different quantisation — the companion recalls
 * different things in-game than it does in chat, and every six-decimal parity check in the engine is
 * guarding an invariant that no longer binds. Treat a change here as a format change.
 *
 * <p>⚠️ <b>Serialise work against the brain on a single-GPU host.</b> Embedding and generation are
 * two processes competing for one accelerator; on the development host, giving both work at the same
 * moment crashes the machine. The natural retrieval flow is already serial — embed the turn, score,
 * then generate — but nothing structural enforces it once several companions are talking at once.
 * {@link #maxConcurrentRequests} bounds this side of it.
 *
 * <p>Overridable by system property or environment variable, matching {@link LlmConfig}:
 * <ul>
 *   <li>{@code -Daicompanion.embeddings.baseUrl=...} or {@code AICOMPANION_EMBEDDINGS_BASEURL}</li>
 *   <li>{@code -Daicompanion.embeddings.model=...} or {@code AICOMPANION_EMBEDDINGS_MODEL}</li>
 * </ul>
 */
public final class EmbeddingsConfig {
    private EmbeddingsConfig() {}

    /**
     * Master switch, default <b>off</b>.
     *
     * <p>Nothing consumes embeddings yet — this client lands ahead of storage, the write path and the
     * retrieval seam, so that the one unknown it can answer (dimension and per-call latency against a
     * real server) is answered before anything is built on top of it. Off by default means a player
     * who has no embedding server sees no errors and pays no latency.
     */
    public static volatile boolean enabled =
            Boolean.parseBoolean(resolve("aicompanion.embeddings.enabled",
                    "AICOMPANION_EMBEDDINGS_ENABLED", "false"));

    /**
     * OpenAI-compatible base URL, <b>no trailing slash and no {@code /v1}</b> — {@code /v1/embeddings}
     * is appended.
     *
     * <p>Defaults to Ollama's port rather than the brain's. Ollama serves {@code /v1/embeddings} in
     * OpenAI shape alongside its native {@code /api/embeddings}, so one code path covers both it and
     * any hosted embedding API.
     */
    public static volatile String baseUrl =
            resolve("aicompanion.embeddings.baseUrl", "AICOMPANION_EMBEDDINGS_BASEURL",
                    "http://localhost:11434");

    /**
     * Model name sent as {@code "model"}. Unlike the brain's, this is <b>not</b> ignorable: Ollama
     * routes on it, and it selects which embedding space the memories live in.
     */
    public static volatile String model =
            resolve("aicompanion.embeddings.model", "AICOMPANION_EMBEDDINGS_MODEL",
                    "nomic-embed-text");

    /**
     * Vector width this build expects, or {@code <= 0} to accept whatever the server returns.
     *
     * <p>768 is not a preference, it is the width every retrieval number was measured at. A server
     * returning something else is not a smaller problem than a server returning an error — it is a
     * larger one, because it works. {@link EmbeddingsService} rejects a mismatch rather than quietly
     * embedding into a space nothing was tuned for.
     */
    public static volatile int expectedDimension =
            Integer.parseInt(resolve("aicompanion.embeddings.expectedDimension",
                    "AICOMPANION_EMBEDDINGS_EXPECTEDDIMENSION", "768"));

    /**
     * Socket read timeout (ms).
     *
     * <p>Deliberately far below the brain's 90s. Embedding one turn is a single forward pass with no
     * generation loop; if it has not returned in a few seconds the server is wedged or swapping a
     * model in, and the right move is to fail and let the companion answer without memory rather than
     * hold the turn.
     */
    public static volatile int timeoutMs =
            Integer.parseInt(resolve("aicompanion.embeddings.timeoutMs",
                    "AICOMPANION_EMBEDDINGS_TIMEOUTMS", "15000"));

    /** Connect timeout (ms) — fail fast when nothing is listening. */
    public static volatile int connectTimeoutMs =
            Integer.parseInt(resolve("aicompanion.embeddings.connectTimeoutMs",
                    "AICOMPANION_EMBEDDINGS_CONNECTTIMEOUTMS", "5000"));

    /**
     * How many embedding requests may be in flight at once. Clamped to 1..8 by
     * {@link EmbeddingsService}.
     *
     * <p>Default 1. A local embedding server processes one batch at a time anyway, so a larger pool
     * only queues inside it — and on a single-GPU host, concurrent embeds are exactly the contention
     * that must not overlap with generation.
     */
    public static volatile int maxConcurrentRequests =
            Integer.parseInt(resolve("aicompanion.embeddings.maxConcurrentRequests",
                    "AICOMPANION_EMBEDDINGS_MAXCONCURRENTREQUESTS", "1"));

    /**
     * Optional bearer token for a hosted embedding API. Blank (default) → no auth header, i.e. a
     * plain local Ollama. Prefer the environment variable so the key never lands on disk.
     */
    public static volatile String apiKey =
            resolve("aicompanion.embeddings.apiKey", "AICOMPANION_EMBEDDINGS_APIKEY", "");

    /** Whether the embeddings API key came from the launch environment rather than the config file. */
    public static boolean apiKeySuppliedByEnv() {
        String v = System.getProperty("aicompanion.embeddings.apiKey");
        if (v == null || v.isBlank()) {
            v = System.getenv("AICOMPANION_EMBEDDINGS_APIKEY");
        }
        return v != null && !v.isBlank();
    }

    private static String resolve(String property, String env, String fallback) {
        String v = System.getProperty(property);
        if (v == null || v.isBlank()) {
            v = System.getenv(env);
        }
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
