package adris.altoclef.player2api;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a turn is <em>about the player at all</em>, or about something the player has told
 * us, before anything is ranked.
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
 * <h2>The three ways in</h2>
 *
 * A turn is admitted if it refers to the player ({@link #PERSONAL}), points at shared history
 * ({@link #RECALL_TRIGGERS}), or <b>names something already in the player's store</b> (see
 * {@link #admits(String, Set)}). The third route was added 2026-08-17 after a measured false
 * negative: <em>"what is the location of home?"</em> was skipped seconds after {@code home} had been
 * stored with coordinates, and the companion answered that it had no home saved. The turn has no
 * pronoun and no trigger word, so the first two routes could not see it — but the word {@code home}
 * was sitting in the store the whole time.
 *
 * <p>It is deliberately not a vocabulary list. Adding {@code where}/{@code location}/{@code home} to
 * the sets above would have fixed that one sentence and missed the next one, and no fixed list can
 * cover memories that do not exist yet: once extraction lands, the store fills with subjects nobody
 * enumerated here. Matching against the store instead means the gate widens exactly as much as the
 * player's memory does, and not at all otherwise.
 *
 * <p>⚠️ <b>Which is also this route's known weakness: selectivity decays as the store grows.</b> Three
 * memories contribute a handful of subject words and the gate stays tight. A thousand memories,
 * written by an extractor rather than typed by hand, will contribute enough of the language that most
 * turns name something — and at that point the gate is closer to "admit everything" than to a guard,
 * with {@link MemoryConfig#minCosine} left holding the whole job again. Nothing here fails at that
 * point; it just quietly stops helping. Watch the {@code gated} counter against the store size, and do
 * not assume the tuning that works at three memories works at three hundred.
 *
 * <h2>⚠️ How much to trust this</h2>
 *
 * <b>It is a heuristic, and it has not been validated.</b> It classifies all 12 turns from the
 * 2026-08-16 session correctly — but several of those turns were written to illustrate the problem
 * this class solves, so scoring well on them is close to circular. Twelve hand-picked examples is
 * not an evaluation. The 2026-08-17 store-aware route is in the same position: it is backed by unit
 * tests, including negative controls, and by one real false negative it demonstrably fixes. That is
 * not the same as being measured.
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
     * Words that carry no identity, dropped when a stored memory is indexed by {@link #tokensOf}.
     *
     * <p>Without this, the memory <em>"I like to build with oak wood"</em> would index {@code i},
     * {@code to} and {@code with} and admit essentially every turn — the store-aware route would
     * become "admit everything" the moment one memory exists, which is the opposite of a gate.
     *
     * <p>Function words only, plus the handful of verbs that appear in stored facts while identifying
     * nothing ({@code like}, {@code build}). Note the asymmetry that makes this safe: a turn using one
     * of these <em>and</em> a pronoun is already admitted by {@link #PERSONAL}, so nothing here can
     * cost a recall that the other routes would have caught.
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "the", "or", "but", "if", "of", "in", "on", "at", "to", "from", "with",
            "for", "by", "as", "is", "am", "are", "was", "were", "be", "been", "being", "do", "does",
            "did", "have", "has", "had", "can", "could", "will", "would", "should", "may", "might",
            "it", "its", "this", "that", "these", "those", "there", "here", "then", "than", "so",
            "not", "no", "yes", "all", "any", "some", "very", "just", "also", "too", "out", "up",
            "down", "over", "about", "into", "instead", "check", "alternatives", "like", "likes",
            "liked", "build", "builds", "building", "built", "want", "wants", "wanted", "use",
            "uses", "using", "get", "gets", "got", "make", "makes", "made", "go", "goes", "going",
            "i", "me", "my", "mine", "myself", "we", "us", "our", "ours", "you", "your", "yours");

    /**
     * The shortest token that may match the store. Two-letter words are almost all function words,
     * and the ones that are not ({@code oak} is three) are not worth the false admissions.
     */
    private static final int MIN_TOKEN_LENGTH = 3;

    /**
     * Word floor when a stored subject is named. Lower than {@link #MIN_WORDS} on purpose:
     * <em>"where's home?"</em> is two words and is the single most likely way to ask for a stored
     * place, while the filler {@code MIN_WORDS} exists to reject ("hi", "lol", "ok") is one word and
     * never overlaps the store.
     */
    private static final int MIN_WORDS_WITH_STORED_SUBJECT = 2;

    /**
     * The distinctive words in a piece of stored memory text, for use as {@code storedTokens}.
     *
     * <p>Callers index their store with this and read turns with the same rules, so a subject cannot
     * be stored in one form and looked up in another.
     */
    public static Set<String> tokensOf(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9']+")) {
            if (w.length() >= MIN_TOKEN_LENGTH && !STOP_WORDS.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    /**
     * Whether this turn should reach the scorer at all, knowing nothing about what is stored.
     *
     * <p>Equivalent to passing an empty store. Prefer {@link #admits(String, Set)} — a caller that has
     * a player has their memories too, and without them a turn naming a stored subject in plain terms
     * ("where's home?") is invisible to the gate.
     */
    public static boolean admits(String turnText) {
        return admits(turnText, Set.of());
    }

    /**
     * Whether this turn should reach the scorer at all.
     *
     * @param turnText     what the player actually typed, with any name prefix already stripped
     * @param storedTokens the distinctive words across this player's stored memories, built with
     *                     {@link #tokensOf}; empty is allowed and simply disables the store-aware route
     */
    public static boolean admits(String turnText, Set<String> storedTokens) {
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
        boolean namesStored = false;
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
            if (!namesStored && storedTokens != null && !storedTokens.isEmpty()
                    && w.length() >= MIN_TOKEN_LENGTH && !STOP_WORDS.contains(w)
                    && storedTokens.contains(w)) {
                namesStored = true;
            }
        }

        if (count < (namesStored ? MIN_WORDS_WITH_STORED_SUBJECT : MIN_WORDS)) {
            return false;
        }
        // Checked before the store, and it wins: "build me a bridge" is an instruction even once
        // "bridge" is a thing we remember. An imperative the companion can execute is still the
        // clearest possible signal that memory is not what is wanted.
        if (first != null && COMMAND_OPENERS.contains(first)) {
            return false;
        }
        // No reference to the player, no pointer at shared history, and nothing we hold is named:
        // there is nothing here for a stored fact to be relevant to. "what's 2 + 2" ends here.
        return personal || trigger || namesStored;
    }

    /** Why a turn was skipped, for the debug log. Never user-facing. */
    public static String explain(String turnText) {
        return explain(turnText, Set.of());
    }

    /**
     * Why a turn was skipped, for the debug log. Never user-facing.
     *
     * <p>Must be read with the same {@code storedTokens} {@link #admits(String, Set)} was given, or it
     * will explain a decision that was not made. Reporting "nothing stored is named" while the store
     * was in fact never consulted is the shape of bug this whole session was spent chasing.
     */
    public static String explain(String turnText, Set<String> storedTokens) {
        if (!MemoryConfig.gateEnabled) {
            return "gate off";
        }
        if (turnText == null || turnText.isBlank()) {
            return "blank";
        }
        String[] words = turnText.toLowerCase(Locale.ROOT).split("[^a-z0-9']+");
        int count = 0;
        String first = null;
        boolean namesStored = false;
        for (String w : words) {
            if (!w.isEmpty()) {
                if (first == null) {
                    first = w;
                }
                count++;
                if (storedTokens != null && w.length() >= MIN_TOKEN_LENGTH
                        && !STOP_WORDS.contains(w) && storedTokens.contains(w)) {
                    namesStored = true;
                }
            }
        }
        int floor = namesStored ? MIN_WORDS_WITH_STORED_SUBJECT : MIN_WORDS;
        if (count < floor) {
            return "too short (" + count + " words, floor " + floor + ")";
        }
        if (first != null && COMMAND_OPENERS.contains(first)) {
            return "opens with a command word (" + first + ")";
        }
        return storedTokens == null || storedTokens.isEmpty()
                ? "no first/second person and no recall trigger (nothing stored to match against)"
                : "no first/second person, no recall trigger, and names nothing in the "
                        + storedTokens.size() + " stored subject(s)";
    }
}
