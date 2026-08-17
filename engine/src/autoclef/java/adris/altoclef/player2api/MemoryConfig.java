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
     * <p>Since {@link CompanionMemory#prefetch} now starts the embedding when the message is
     * <em>queued</em>, this budget is normally not spent at all — the vector is already there by
     * the time the turn dispatches. It only bites when prefetch missed, which makes a wider ceiling
     * close to free: 250 ms costs nothing in the common case and absorbs a GC pause or a model that
     * has just been swapped back in, either of which would otherwise silently drop a recall.
     */
    public static volatile int embedBudgetMs =
            Integer.parseInt(resolve("aicompanion.memory.embedBudgetMs",
                    "AICOMPANION_MEMORY_EMBEDBUDGETMS", "250"));

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
     * <p>⚠️ <b>This default assumes {@link #gateEnabled} is on.</b> {@link MemoryGate} removes the
     * meaningless turns before anything is scored, so this no longer has to do that job and can sit
     * at 0.45 — which recovers the two legitimate recalls a 0.50 floor was costing. Turning the gate
     * off without raising this back to 0.50 puts the noise straight back in.
     */
    public static volatile double minCosine =
            Double.parseDouble(resolve("aicompanion.memory.minCosine",
                    "AICOMPANION_MEMORY_MINCOSINE", "0.45"));

    /**
     * Drop any memory more than this far behind the best one, even if it clears {@link #minCosine}.
     *
     * <p>This exists because the absolute scale is compressed: against {@code nomic-embed-text} the
     * seed facts score between roughly 0.43 and 0.68 whether or not they are relevant, so a floor
     * cannot separate "second best" from "filler". A relative test can, and it expresses the right
     * idea — <em>when one memory is clearly ahead, take only it; when several are tied, the embedder
     * is not discriminating between them, so take the tied group.</em>
     *
     * <p>Calibrated 2026-08-16 on the eight measured turns. The result is flat from 0.03 to 0.08
     * (mean 1.38 memories per turn, versus about 2.1 with no margin), so 0.05 sits mid-plateau
     * rather than on a knife-edge. Five of the eight turns come back with exactly the one correct
     * fact; the three that keep a second are the ones where the top two are within 0.02 of each
     * other, which is precisely the tie case this is meant to preserve.
     *
     * <p>Set to 0 or less to disable and fall back to {@link #topK} plus {@link #minCosine} alone.
     */
    public static volatile double relativeMargin =
            Double.parseDouble(resolve("aicompanion.memory.relativeMargin",
                    "AICOMPANION_MEMORY_RELATIVEMARGIN", "0.05"));

    /**
     * Whether to decide "is this turn about the player at all" before ranking anything.
     *
     * <p>On by default, and it is the mechanism that makes {@link #minCosine} tractable — see
     * {@link MemoryGate} for why a threshold alone provably cannot do this job. Turn it off to see
     * raw retrieval behaviour, and raise {@code minCosine} to 0.50 if you do.
     */
    public static volatile boolean gateEnabled =
            Boolean.parseBoolean(resolve("aicompanion.memory.gateEnabled",
                    "AICOMPANION_MEMORY_GATEENABLED", "true"));

    private static String resolve(String property, String env, String fallback) {
        String v = System.getProperty(property);
        if (v == null || v.isBlank()) {
            v = System.getenv(env);
        }
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
