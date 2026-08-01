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
     * Below this, {@link #maxTokens} is low enough to break features rather than merely cap chat.
     *
     * <p>Skills hand the model a command to reproduce verbatim, and the farming one is 693 characters
     * — around 200 tokens before {@code reason}, {@code message} and the JSON scaffolding are counted.
     * At the old default of 200 the reply was cut off mid-string on every attempt, so the skill could
     * never run at all: measured 2026-07-29, four calls, zero commands, nothing built.
     *
     * <p>Raising the cap is close to free — {@code max_tokens} bounds a reply, and billing is on the
     * tokens actually generated, so a high cap costs nothing for a short answer. Spend is bounded by
     * {@code behavior.maxAutonomousTurns}, {@code triggerPrefix}, {@code thinkThrottleSeconds} and the
     * request cap instead.
     *
     * <p>Only meaningful for a positive {@code maxTokens}: {@code <= 0} means "omit, use the server
     * default", which is unlimited rather than too low, and must never be warned about.
     */
    public static final int MIN_USEFUL_MAX_TOKENS = 1000;

    /** Whether {@link #maxTokens} is set low enough to truncate skill commands. */
    public static boolean maxTokensTooLow() {
        return maxTokens > 0 && maxTokens < MIN_USEFUL_MAX_TOKENS;
    }

    /**
     * Whether to constrain output to a JSON object ({@code response_format: json_object}).
     *
     * <p>Defaults to true. Left off originally because early testing showed clean JSON without it —
     * that held for short exchanges and stopped holding in longer, chattier sessions: a measured
     * session had 9 of 21 turns come back as bare prose, which the parser can only turn into a spoken
     * message with no command, so the companion narrates work it never starts. Enabling it dropped
     * that to 0. Honored by xAI, OpenAI and llama.cpp alike.
     *
     * <p>Note this guarantees valid JSON, not the right fields — a model can still return a
     * well-formed object with an empty command.
     */
    public static volatile boolean useGrammar =
            Boolean.parseBoolean(resolve("aicompanion.llm.useGrammar", "AICOMPANION_LLM_USEGRAMMAR", "true"));

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
     * How many LLM requests may be in flight at once, across every companion.
     *
     * <p>This is what stops one busy companion from freezing the others: at 1 the roster is
     * effectively single-file, which is what the old shared single completer amounted to. It is also
     * the concurrency half of the spend guardrail — {@link #maxRequests} caps requests per session,
     * this caps how many can be burning at the same instant.
     *
     * <p>2 suits a local llama.cpp, which serves one request at a time anyway, so a larger pool only
     * queues inside the server. Raise it for a hosted endpoint that parallelises, or when several
     * companions are out and expected to work independently. Clamped to 1..16.
     */
    public static volatile int maxConcurrentRequests =
            Integer.parseInt(resolve("aicompanion.llm.maxConcurrentRequests",
                    "AICOMPANION_LLM_MAXCONCURRENTREQUESTS", "2"));

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
