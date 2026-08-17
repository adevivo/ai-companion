package adris.altoclef.player2api;

import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a turn is <em>about the player at all</em>, before anything is ranked.
 *
 * <h2>Why this is not a threshold</h2>
 *
 * Retrieval scores which memory best fits a turn. It has no way to answer "should any memory fit?",
 * because the scorer always returns its top k in order. That gap is invisible to the memory engine's
 * own evaluation — every measurement there is recall@k over questions that each had one correct
 * memory to find, so "nothing should have been returned" was never a scoreable outcome.
 *
 * <p>It cannot be closed with a cosine floor either. Measured 2026-08-16 against
 * {@code nomic-embed-text}:
 *
 * <pre>
 *   lowest cosine for a CORRECT recall   ("where's my dog?")      0.455
 *   highest cosine on a MEANINGLESS turn ("attack that zombie")   0.510
 * </pre>
 *
 * The distributions overlap, so any floor admitting the first admits the second. The two questions
 * are genuinely different and need different mechanisms: this class answers the first, and
 * {@link MemoryConfig#minCosine} answers the second <em>within</em> a turn that has already passed
 * here.
 *
 * <p>Because the gate removes the meaningless turns entirely, the floor no longer has to do that job
 * and can sit lower than it otherwise would — which is how the two recalls that a 0.50 floor was
 * costing come back.
 *
 * <h2>⚠️ How much to trust this</h2>
 *
 * <b>It is a heuristic, and it has not been validated.</b> It classifies all 12 turns from the
 * 2026-08-16 session correctly — but several of those turns were written to illustrate the problem
 * this class solves, so scoring well on them is close to circular. Twelve hand-picked examples is
 * not an evaluation.
 *
 * <p>What would actually validate it: a batch of real player turns, labelled for "should this have
 * recalled anything?" before the gate is run over them. That is the same pooled-judgement work the
 * memory POC already has on its list, and this is a second reason to do it.
 *
 * <p>It is deliberately cheap and local — no model call, no embedding. A gate that cost an LLM turn
 * to decide whether to spend an embedding would be worse than the problem. It is also deliberately
 * biased toward <em>skipping</em>: a turn wrongly skipped leaves the companion exactly as it behaves
 * with no memory at all, while a turn wrongly admitted makes it volunteer something nobody asked
 * about.
 */
public final class MemoryGate {
    private MemoryGate() {}

    /** Below this, a turn is filler — "hi", "lol", "ok", "nice". */
    private static final int MIN_WORDS = 3;

    /**
     * Openers that mean "do this", not "let's talk about me".
     *
     * <p>Matched as the FIRST word only. "build" opening a line is an instruction; "what should I
     * build this out of" is a question about the player's preferences and must still reach the
     * scorer. Kept in step with the valid-command list in {@link Prompts} — an imperative the
     * companion can execute is the clearest possible signal that memory is not what is wanted.
     */
    private static final Set<String> COMMAND_OPENERS = Set.of(
            "attack", "goto", "go", "get", "follow", "build", "mine", "farm", "fish", "stop",
            "dig", "equip", "give", "deposit", "idle", "scan", "come", "craft", "kill", "drop",
            "wait", "here", "hello", "hi", "hey", "yo", "sup", "thanks", "ty", "ok", "okay");

    /**
     * First and second person markers. A memory is a fact about the player, so a turn that never
     * refers to the player or to the pair of them is unlikely to want one.
     */
    private static final Set<String> PERSONAL = Set.of(
            "i", "i'm", "im", "i've", "ive", "i'd", "i'll", "me", "my", "mine", "myself",
            "we", "we're", "were", "we've", "weve", "we'd", "we'll", "us", "our", "ours",
            "you", "you're", "youre", "your", "yours");

    /** Words that point at shared history even without a pronoun — "remember the temple?" */
    private static final Set<String> RECALL_TRIGGERS = Set.of(
            "remember", "remembered", "forget", "forgot", "forgotten", "again", "still", "ever",
            "before", "last", "used", "always", "usually", "favourite", "favorite", "prefer",
            "prefers", "preferred", "normally", "again?", "earlier");

    /**
     * Whether this turn should reach the scorer at all.
     *
     * @param turnText what the player actually typed, with any name prefix already stripped
     */
    public static boolean admits(String turnText) {
        if (!MemoryConfig.gateEnabled) {
            return true;
        }
        if (turnText == null || turnText.isBlank()) {
            return false;
        }

        String[] words = turnText.toLowerCase(Locale.ROOT).split("[^a-z0-9']+");
        int count = 0;
        boolean personal = false;
        boolean trigger = false;
        String first = null;

        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (first == null) {
                first = w;
            }
            count++;
            if (PERSONAL.contains(w)) {
                personal = true;
            }
            if (RECALL_TRIGGERS.contains(w)) {
                trigger = true;
            }
        }

        if (count < MIN_WORDS) {
            return false;
        }
        if (first != null && COMMAND_OPENERS.contains(first)) {
            return false;
        }
        // Neither a reference to the player nor a pointer at shared history: there is nothing here
        // for a fact about the player to be relevant to. "what's 2 + 2" ends here.
        return personal || trigger;
    }

    /** Why a turn was skipped, for the debug log. Never user-facing. */
    public static String explain(String turnText) {
        if (!MemoryConfig.gateEnabled) {
            return "gate off";
        }
        if (turnText == null || turnText.isBlank()) {
            return "blank";
        }
        String[] words = turnText.toLowerCase(Locale.ROOT).split("[^a-z0-9']+");
        int count = 0;
        String first = null;
        for (String w : words) {
            if (!w.isEmpty()) {
                if (first == null) {
                    first = w;
                }
                count++;
            }
        }
        if (count < MIN_WORDS) {
            return "too short (" + count + " words)";
        }
        if (first != null && COMMAND_OPENERS.contains(first)) {
            return "opens with a command word (" + first + ")";
        }
        return "no first/second person and no recall trigger";
    }
}
