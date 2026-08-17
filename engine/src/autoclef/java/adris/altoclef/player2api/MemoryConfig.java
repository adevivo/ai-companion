package adris.altoclef.player2api;

/**
 * Long-term memory retrieval — the vertical slice.
 *
 * <p>This exists to answer one question before anything is built to persist memories: <b>does
 * recalling the right fact make the companion better?</b> So it deliberately has no storage, no
 * encryption and no write path. The facts are hard-coded in {@link CompanionMemory}, they live only
 * in RAM, and they are the same for every player. Nothing here is shippable, and the config is off
 * by default.
 *
 * <p>Retrieval itself is the measured part and is not being re-litigated here: relevance-only
 * scoring, k=5–10, int8 scan with a float32 rescore. See {@code ../ai-companion-memory}.
 */
public final class MemoryConfig {
    private MemoryConfig() {}

    /**
     * Master switch, default off. Requires {@link EmbeddingsConfig#enabled} as well — memory without
     * an embedder cannot rank anything, and turning this on alone does nothing but log a warning.
     */
    public static volatile boolean enabled =
            Boolean.parseBoolean(resolve("aicompanion.memory.enabled",
                    "AICOMPANION_MEMORY_ENABLED", "false"));

    /**
     * How many memories reach the prompt.
     *
     * <p>The measured guidance is k=5–10, and <b>3 is deliberately below it.</b> That guidance comes
     * from recall@k on questions that each had exactly one correct answer to find, where a larger k
     * can only help. In conversation the cost is reversed: the top hit is almost always the right
     * one (7 of 8 measured turns, the eighth a near-tie), so ranks 2–5 are mostly filler that costs
     * tokens on every turn and gives the model more chances to bring up something nobody asked
     * about.
     */
    public static volatile int topK =
            Integer.parseInt(resolve("aicompanion.memory.topK", "AICOMPANION_MEMORY_TOPK", "3"));

    /**
     * Hard ceiling, in milliseconds, on how long a turn may wait for the query embedding.
     *
     * <p>⚠️ <b>This is a bounded block on the server thread, and it is the known flaw in this
     * slice.</b> The packet is assembled in {@code AgentConversationData.process()}, which runs on
     * the server thread, and the embedding is a network call. Measured warm latency is ~16 ms — a
     * third of a 50 ms tick, once per conversation turn, which is a hitch nobody will see. A cold
     * model load (~500 ms) or an embedder that has gone away (connect timeout) would be very
     * visible, so the wait is capped and a miss simply means the turn runs without memories.
     *
     * <p>120 ms leaves room for a slow-but-working call while staying under three ticks. The real
     * fix is to embed when the message is queued rather than when the turn dispatches — the event
     * sits in the queue for at least a tick, which is ample — and that is the first thing to do if
     * this slice is kept.
     */
    public static volatile int embedBudgetMs =
            Integer.parseInt(resolve("aicompanion.memory.embedBudgetMs",
                    "AICOMPANION_MEMORY_EMBEDBUDGETMS", "120"));

    /**
     * Drop memories whose cosine similarity to the turn falls below this.
     *
     * <p>Without a floor, top-k always returns k memories — including on "hi", where the best match
     * is merely the least irrelevant fact. Injecting those is what makes memory feel intrusive
     * rather than useful.
     *
     * <p>⚠️ <b>Measured 2026-08-16, and the honest result is that this cannot be made to work
     * cleanly.</b> Against {@code nomic-embed-text} and the seed facts, the two distributions
     * overlap:
     *
     * <pre>
     *   lowest cosine for a CORRECT recall  ("where's my dog?")     0.455
     *   highest cosine on a MEANINGLESS turn ("attack that zombie")  0.510
     * </pre>
     *
     * <p>A meaningless turn out-scores a genuine hit, so <b>no single threshold separates them.</b>
     * 0.50 is chosen to favour precision over recall: it drops "hi" (0.497), "lol" (0.478) and
     * "what's 2 + 2" (0.401), at the cost of also dropping two legitimate recalls — "where's my
     * dog?" (0.455) and "where do we live again?" (0.489).
     *
     * <p>That trade is deliberate. A missed memory leaves the companion behaving exactly as it does
     * today, which is merely neutral; an injected irrelevant memory makes it volunteer something
     * nobody asked about, which is worse than today. Precision is the side to err on until there is
     * something better.
     *
     * <p>The real fix is not a better constant. It is deciding <em>whether this turn is about the
     * player at all</em> before ranking anything — "attack that zombie" should never have reached
     * the scorer. That is a gate, not a threshold, and it is unbuilt.
     */
    public static volatile double minCosine =
            Double.parseDouble(resolve("aicompanion.memory.minCosine",
                    "AICOMPANION_MEMORY_MINCOSINE", "0.50"));

    private static String resolve(String property, String env, String fallback) {
        String v = System.getProperty(property);
        if (v == null || v.isBlank()) {
            v = System.getenv(env);
        }
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
