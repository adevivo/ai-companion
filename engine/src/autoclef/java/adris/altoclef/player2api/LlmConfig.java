package adris.altoclef.player2api;

/**
 * Brain endpoint configuration for the AI Companion fork.
 *
 * <p>In {@code localMode} (default) the agent talks to a local, OpenAI-compatible endpoint
 * (llama.cpp on :3030) with no Player2 cloud auth, TTS, STT, or heartbeat. Overridable via
 * system properties or environment variables so it can be set from the launch command / config
 * without recompiling:
 * <ul>
 *   <li>{@code -Daicompanion.llm.baseUrl=...} or {@code AICOMPANION_LLM_BASEURL}</li>
 *   <li>{@code -Daicompanion.llm.localMode=false} or {@code AICOMPANION_LLM_LOCALMODE}</li>
 * </ul>
 *
 * <p>Phase 3 will feed these from the consumer mod's JSON config.
 */
public final class LlmConfig {
    private LlmConfig() {}

    /** OpenAI-compatible base URL (llama.cpp server). Endpoints like {@code /v1/chat/completions} are appended. */
    public static volatile String baseUrl =
            resolve("aicompanion.llm.baseUrl", "AICOMPANION_LLM_BASEURL", "http://localhost:3030");

    /** When true, skip all Player2 cloud coupling (auth/heartbeat/TTS/STT) and use {@link #baseUrl}. */
    public static volatile boolean localMode =
            Boolean.parseBoolean(resolve("aicompanion.llm.localMode", "AICOMPANION_LLM_LOCALMODE", "true"));

    /** Socket read timeout (ms). A slow/stuck model call fails instead of wedging the single-track brain. */
    public static volatile int timeoutMs =
            Integer.parseInt(resolve("aicompanion.llm.timeoutMs", "AICOMPANION_LLM_TIMEOUTMS", "90000"));

    /** Connect timeout (ms) — fail fast if the endpoint is down. */
    public static volatile int connectTimeoutMs = 10000;

    /**
     * Model name sent as {@code "model"} in the request body. llama.cpp ignores it (serves whatever is
     * loaded), but OpenAI-compatible proxies need it. Blank/null → omitted from the body.
     */
    public static volatile String model =
            resolve("aicompanion.llm.model", "AICOMPANION_LLM_MODEL", "local");

    /** Sampling temperature. Sentinel {@code < 0} → omit (use the server's default). */
    public static volatile double temperature =
            Double.parseDouble(resolve("aicompanion.llm.temperature", "AICOMPANION_LLM_TEMPERATURE", "-1"));

    /** Max tokens to generate ({@code max_tokens}). Sentinel {@code <= 0} → omit (server default). */
    public static volatile int maxTokens =
            Integer.parseInt(resolve("aicompanion.llm.maxTokens", "AICOMPANION_LLM_MAXTOKENS", "-1"));

    /**
     * Whether to constrain output with a GBNF grammar / json_schema. Carried from config for later use;
     * Phase 2 testing showed clean JSON without it, so the request body does not yet emit a grammar.
     */
    public static volatile boolean useGrammar =
            Boolean.parseBoolean(resolve("aicompanion.llm.useGrammar", "AICOMPANION_LLM_USEGRAMMAR", "false"));

    /**
     * Optional bearer token for a hosted OpenAI-compatible endpoint (e.g. xAI/Grok, OpenAI). When
     * non-blank, sent as {@code Authorization: Bearer <apiKey>} even in {@link #localMode} — so you can
     * point {@link #baseUrl} at a frontier API for A/B testing while keeping Player2 cloud auth/heartbeat
     * off. Blank (default) → no auth header, i.e. plain local llama.cpp. Prefer the env var so the key is
     * not written to disk.
     */
    public static volatile String apiKey =
            resolve("aicompanion.llm.apiKey", "AICOMPANION_LLM_APIKEY", "");

    /**
     * Cost guardrail for frontier testing: max LLM requests per server session ({@code <= 0} = unlimited,
     * the default). Once exceeded, calls fail fast with a clear message instead of hitting the paid API —
     * so a runaway feedback loop can't quietly rack up spend while you are away.
     */
    public static volatile int maxRequests =
            Integer.parseInt(resolve("aicompanion.llm.maxRequests", "AICOMPANION_LLM_MAXREQUESTS", "0"));

    /**
     * Report cumulative token usage to the owner every N total tokens ({@code <= 0} = never). Unlike
     * {@link #maxRequests} this never blocks a call — it just keeps you aware of spend on a paid
     * endpoint. Counts come from the {@code usage} object OpenAI-compatible servers return (llama.cpp
     * and xAI both do); if a server omits it, no report is emitted.
     */
    public static volatile long usageReportEveryTokens =
            Long.parseLong(resolve("aicompanion.llm.usageReportEveryTokens",
                    "AICOMPANION_LLM_USAGEREPORTEVERYTOKENS", "100000"));

    private static String resolve(String property, String env, String fallback) {
        String v = System.getProperty(property);
        if (v == null || v.isBlank()) {
            v = System.getenv(env);
        }
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
