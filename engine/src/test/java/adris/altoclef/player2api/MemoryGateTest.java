package adris.altoclef.player2api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Controls for {@link MemoryGate}, the guard that decides whether a turn reaches the scorer.
 *
 * <h2>Why these are mostly negative</h2>
 *
 * The gate's job is to <em>refuse</em>. A test suite that only checked what it admits would pass
 * completely for a gate that admits everything, which is the failure mode with a real cost: a
 * companion volunteering stored facts nobody asked about. So most of what follows asserts that
 * something is still skipped, and the assertions that matter most are the ones that must keep failing
 * to admit even after the store fills up.
 *
 * <h2>What this suite does not do</h2>
 *
 * It does not validate the heuristic. Every turn here was chosen by the same people who wrote the
 * rules, several of them after watching the rules fail, and scoring well on your own examples is close
 * to circular — see the warning in {@link MemoryGate}'s own docs. What would validate it is a batch of
 * real player turns labelled for "should this have recalled anything?" <em>before</em> the gate is run
 * over them. These tests catch regressions in what we already decided; they cannot tell us the
 * decisions were right.
 */
class MemoryGateTest {

    /**
     * What the user's store actually held on 2026-08-17, tokenised.
     *
     * <p>Two memories: a preference, and a place. Taken from the real store rather than invented, so
     * the session replays below are replays and not fiction.
     */
    private static final Set<String> REAL_STORE = tokens(
            "I like to build with oak wood but you can check if you have alternatives instead",
            "home");

    private static final Set<String> EMPTY_STORE = Set.of();

    private boolean gateWasEnabled;

    @BeforeEach
    void enableGate() {
        gateWasEnabled = MemoryConfig.gateEnabled;
        MemoryConfig.gateEnabled = true;
    }

    @AfterEach
    void restoreGate() {
        MemoryConfig.gateEnabled = gateWasEnabled;
    }

    private static Set<String> tokens(String... memories) {
        java.util.HashSet<String> all = new java.util.HashSet<>();
        for (String memory : memories) {
            all.addAll(MemoryGate.tokensOf(memory));
        }
        return Set.copyOf(all);
    }

    @Nested
    @DisplayName("the false negative this route was added for")
    class StoredSubjectRegression {

        /**
         * The exact turn from the 14:48 session. It was skipped 12 seconds after {@code home} had been
         * stored with coordinates, and the companion answered "I don't have a saved home location in
         * memory" — denying something it held. No pronoun, no trigger word, so the original two routes
         * could not see it.
         */
        @Test
        void admitsTheTurnThatWasWronglySkipped() {
            assertTrue(MemoryGate.admits("what is the location of home?", REAL_STORE));
        }

        /** Two words, which the old three-word floor rejected on top of everything else. */
        @Test
        void admitsTheShortestFormOfTheSameQuestion() {
            assertTrue(MemoryGate.admits("where's home?", REAL_STORE));
        }

        /**
         * The other half of the claim, and the one that makes this a store-aware route rather than a
         * vocabulary list: with nothing stored, the same turns are still refused. If these ever start
         * passing, someone has added {@code where}/{@code home} to a word set and the route has
         * stopped tracking the store.
         */
        @Test
        void skipsTheSameTurnsWhenNothingIsStored() {
            assertFalse(MemoryGate.admits("what is the location of home?", EMPTY_STORE));
            assertFalse(MemoryGate.admits("where's home?", EMPTY_STORE));
        }

        /** A subject the store has never heard of stays out, however place-shaped the sentence is. */
        @Test
        void skipsQuestionsAboutSubjectsNotStored() {
            assertFalse(MemoryGate.admits("what is the location of the temple?", REAL_STORE));
        }

        /**
         * What phase 2c buys for free. Nothing about bridges is stored today, which is exactly why the
         * companion answered "no bridge started yet" to a question about a bridge it had been helping
         * build; once extraction writes that fact, the same turn admits with no gate change at all.
         */
        @Test
        void admitsOnceExtractionHasStoredTheSubject() {
            Set<String> afterExtraction = tokens("we are building a bridge over the ravine");
            assertFalse(MemoryGate.admits("how is the bridge coming along?", REAL_STORE));
            assertTrue(MemoryGate.admits("how is the bridge coming along?", afterExtraction));
        }
    }

    @Nested
    @DisplayName("extraction asks a different question from retrieval")
    class ExtractionGate {

        @Test
        @DisplayName("the third-person continuation that cost three real facts is admitted")
        void thirdPersonContinuationAdmitted() {
            // 2026-08-18: the only turn of four that held a durable fact, and the only one
            // extraction never saw — refused for having no first- or second-person pronoun.
            assertTrue(MemoryGate.admitsForExtraction(
                    "he's a brown dog; pitbull lab mix. He's a sweetheart but kinda a sissy wimp"));
            // And retrieval still refuses it, which is correct and must not drift.
            assertFalse(MemoryGate.admits(
                    "he's a brown dog; pitbull lab mix. He's a sweetheart but kinda a sissy wimp"));
        }

        @Test
        @DisplayName("the extractor's own world-fact example is admitted without a pronoun")
        void worldFactWithoutPronounAdmitted() {
            // Brief 3 prescribes this exact sentence for testing world scoping, and it is the
            // extractor prompt's worked example. Under the retrieval gate it silently did nothing,
            // so the test would have read as "the world-fact path is broken".
            assertTrue(MemoryGate.admitsForExtraction("the base is in the taiga north of spawn"));
        }

        @Test
        @DisplayName("an instruction is still refused — the syntactic filters are kept")
        void commandsStillRefused() {
            assertFalse(MemoryGate.admitsForExtraction("go grab me ten logs"));
            assertFalse(MemoryGate.admitsForExtraction("attack that zombie"));
            assertFalse(MemoryGate.admitsForExtraction("follow me"));
        }

        @Test
        @DisplayName("filler is still refused")
        void fillerStillRefused() {
            assertFalse(MemoryGate.admitsForExtraction("ok"));
            assertFalse(MemoryGate.admitsForExtraction("lol"));
            assertFalse(MemoryGate.admitsForExtraction("hi"));
            assertFalse(MemoryGate.admitsForExtraction(""));
            assertFalse(MemoryGate.admitsForExtraction(null));
        }

        @Test
        @DisplayName("relaxing extraction does not relax retrieval")
        void retrievalIsUnchanged() {
            // The load-bearing control for this whole change. Retrieval's caution is justified by an
            // asymmetry that does not apply to writing, so its behaviour must not move at all.
            assertFalse(MemoryGate.admits("what's 2 + 2"));
            assertFalse(MemoryGate.admits("the base is in the taiga north of spawn"));
            assertTrue(MemoryGate.admits("I always build with spruce"));
            assertTrue(MemoryGate.admits("do you remember the temple"));
        }

        @Test
        @DisplayName("the skip reason names the syntactic rule that fired")
        void explanationsAreSpecific() {
            assertTrue(MemoryGate.explainForExtraction("ok").contains("too short"));
            assertTrue(MemoryGate.explainForExtraction("go grab me ten logs")
                    .contains("command word"));
            assertEquals("admitted",
                    MemoryGate.explainForExtraction("he's a brown dog and a sweetheart"));
        }
    }

    @Nested
    @DisplayName("negative controls — still refused with a full store")
    class NegativeControls {

        /** The docblock's measured worst case: cosine 0.510 on a turn that means nothing to memory. */
        @Test
        void skipsCommands() {
            assertFalse(MemoryGate.admits("attack that zombie", REAL_STORE));
            assertFalse(MemoryGate.admits("mine some iron for me", REAL_STORE));
        }

        /**
         * A command opener wins even when the store knows the subject. "build me a bridge" is an
         * instruction, not a question about a bridge, and the gate must keep preferring that reading.
         */
        @Test
        void commandOpenerBeatsAStoredSubject() {
            Set<String> store = tokens("we are building a bridge over the ravine");
            assertFalse(MemoryGate.admits("build the bridge higher please", store));
            assertFalse(MemoryGate.admits("come to the bridge", store));
        }

        @Test
        void skipsFiller() {
            assertFalse(MemoryGate.admits("hi", REAL_STORE));
            assertFalse(MemoryGate.admits("lol ok", REAL_STORE));
            assertFalse(MemoryGate.admits("", REAL_STORE));
            assertFalse(MemoryGate.admits(null, REAL_STORE));
        }

        /** One stored word is not a licence to admit a one-word turn. */
        @Test
        void skipsASingleStoredWordOnItsOwn() {
            assertFalse(MemoryGate.admits("home", REAL_STORE));
        }

        @Test
        void skipsImpersonalQuestionsAboutTheWorldAtLarge() {
            assertFalse(MemoryGate.admits("what's 2 + 2", REAL_STORE));
            assertFalse(MemoryGate.admits("how many diamonds does a pickaxe need", REAL_STORE));
        }

        /**
         * The load-bearing control for the stoplist. The stored preference contains "build", "with"
         * and "check"; if those counted as subjects, a store with one memory in it would admit most
         * generic Minecraft chatter. Note both turns are pronoun-free on purpose — with "you" or "I"
         * in them they would be admitted by the PERSONAL route and would prove nothing about tokens.
         */
        @Test
        void commonWordsInAStoredFactAreNotSubjects() {
            assertFalse(MemoryGate.admits("how to build a piston door", REAL_STORE));
            assertFalse(MemoryGate.admits("check the furnace instead", REAL_STORE));
        }

        /** ...while the distinctive words in the same memory are. */
        @Test
        void distinctiveWordsInAStoredFactAreSubjects() {
            assertTrue(MemoryGate.admits("does oak grow here", REAL_STORE));
        }
    }

    @Nested
    @DisplayName("the two original routes still work")
    class ExistingRoutes {

        @Test
        void admitsOnAPersonalPronounWithNothingStored() {
            assertTrue(MemoryGate.admits("what is my favourite building material", EMPTY_STORE));
            assertTrue(MemoryGate.admits("what do you know about me", EMPTY_STORE));
        }

        @Test
        void admitsOnARecallTrigger() {
            assertTrue(MemoryGate.admits("remember the temple we found", EMPTY_STORE));
        }

        @Test
        void gateOffAdmitsEverything() {
            MemoryConfig.gateEnabled = false;
            assertTrue(MemoryGate.admits("attack that zombie", EMPTY_STORE));
            assertTrue(MemoryGate.admits("hi", EMPTY_STORE));
        }

        /** A caller with no store handy must not crash the turn. */
        @Test
        void toleratesANullStore() {
            assertTrue(MemoryGate.admits("what is my favourite wood", null));
            assertFalse(MemoryGate.admits("attack that zombie", null));
        }
    }

    @Nested
    @DisplayName("tokenisation")
    class Tokenisation {

        @Test
        void keepsDistinctiveWordsAndDropsFunctionWords() {
            Set<String> t = MemoryGate.tokensOf(
                    "I like to build with oak wood but you can check if you have alternatives instead");
            assertTrue(t.contains("oak"));
            assertTrue(t.contains("wood"));
            for (String noise : new String[] {"i", "to", "with", "you", "can", "if", "have",
                    "build", "like", "check", "alternatives", "instead", "but"}) {
                assertFalse(t.contains(noise), "expected '" + noise + "' to be dropped as noise");
            }
        }

        @Test
        void dropsShortTokens() {
            assertFalse(MemoryGate.tokensOf("my ox is at 12").contains("ox"));
        }

        @Test
        void isCaseInsensitive() {
            assertEquals(MemoryGate.tokensOf("Oak Wood"), MemoryGate.tokensOf("oak wood"));
        }

        @Test
        void handlesBlankAndNull() {
            assertTrue(MemoryGate.tokensOf(null).isEmpty());
            assertTrue(MemoryGate.tokensOf("   ").isEmpty());
        }
    }

    /**
     * The four turns from the 14:56 session, replayed against the store as it stood.
     *
     * <p>Every one of these was gated correctly at the time — the two failures that session were a
     * cold-start embedder timeout and an empty store, neither of which is the gate's doing. They are
     * here so a future change to the word sets cannot quietly break turns that already worked.
     */
    @Nested
    @DisplayName("session replay, 2026-08-17 14:56")
    class SessionReplay {

        @Test
        void allFourTurnsReachTheScorer() {
            // "my" — the recall that then failed on the embedder's model load, not here.
            assertTrue(MemoryGate.admits("what's my favorite building material", REAL_STORE));
            // "we" — admitted, then correctly dropped by the cosine floor at 0.396, nothing stored
            // about bridges. The gate admitting a turn it has no answer for is the intended split of
            // labour: this class decides whether to look, minCosine decides whether it found anything.
            assertTrue(MemoryGate.admits("how are we doing with the bridge construction?", REAL_STORE));
            // "my" — floor rejected it at 0.389.
            assertTrue(MemoryGate.admits("that's my dog's name?", REAL_STORE));
            // "my" plus the stored subject "home" — the one that worked end to end, at 0.841.
            assertTrue(MemoryGate.admits("where is my home?", REAL_STORE));
        }

        /** And the turn from the session before, which is the whole reason this route exists. */
        @Test
        void theTurnFrom1448NowReachesTheScorerToo() {
            assertTrue(MemoryGate.admits("what is the location of home?", REAL_STORE));
        }
    }

    @Nested
    @DisplayName("explain() reports the decision that was actually made")
    class Explanations {

        /**
         * {@code explain} takes the same tokens as {@code admits} because a diagnostic that describes a
         * different decision from the one taken is worse than none — this session was spent chasing
         * exactly that shape of bug, in a feedback message that reported intent instead of outcome.
         */
        @Test
        void mentionsTheStoreWhenOneWasConsulted() {
            String why = MemoryGate.explain("what is the location of the temple?", REAL_STORE);
            assertTrue(why.contains("stored subject"), why);
        }

        @Test
        void saysSoWhenThereWasNothingToMatchAgainst() {
            String why = MemoryGate.explain("what is the location of home?", EMPTY_STORE);
            assertTrue(why.contains("nothing stored"), why);
        }

        @Test
        void reportsTheFloorThatWasApplied() {
            assertTrue(MemoryGate.explain("home", REAL_STORE).contains("floor 2"));
            assertTrue(MemoryGate.explain("lol ok", EMPTY_STORE).contains("floor 3"));
        }
    }
}
