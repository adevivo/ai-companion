package adris.altoclef.player2api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.neovetta.aicompanion.memory.MemoryScope;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Controls for {@link MemoryExtractor} — the code half of extraction, which is all of the deciding.
 *
 * <h2>What is testable here and what is not</h2>
 *
 * Everything below runs without a model. That is the point of the split: the model emits fields and
 * code makes every decision, so the decisions are unit-testable and the only untested part is whether
 * a model fills the fields sensibly. That second question cannot be settled by tests at all — two
 * competent models agreed only 32% of the time on whether a turn even holds a fact — so it needs a
 * batch of turns labelled before extraction runs over them, which does not exist yet.
 *
 * <p>The tests therefore assert the parts that must not drift: the vocabulary is closed, scope is
 * decided by table, a fact about someone else never enters this player's store, and a malformed reply
 * costs nothing.
 */
class MemoryExtractorTest {

    private static final String OWNER = "Dauk808";

    private static List<MemoryExtractor.Candidate> plan(String json) {
        return MemoryExtractor.plan(MemoryExtractor.parse(json), OWNER);
    }

    @Nested
    @DisplayName("parsing is defensive — a bad reply costs nothing")
    class Parsing {

        @Test
        void readsAWellFormedFact() {
            List<MemoryExtractor.ExtractedFact> facts = MemoryExtractor.parse(
                    "{\"facts\":[{\"subject\":\"user\",\"predicate\":\"prefers\","
                            + "\"value\":\"spruce for building\",\"about\":\"user\"}]}");
            assertEquals(1, facts.size());
            assertEquals("prefers", facts.get(0).predicate());
            assertEquals("spruce for building", facts.get(0).value());
        }

        /** The common case, and it must look like success rather than failure. */
        @Test
        void anEmptyArrayIsNormal() {
            assertTrue(MemoryExtractor.parse("{\"facts\":[]}").isEmpty());
        }

        @Test
        void survivesEveryShapeOfMalformedReply() {
            assertTrue(MemoryExtractor.parse(null).isEmpty());
            assertTrue(MemoryExtractor.parse("").isEmpty());
            assertTrue(MemoryExtractor.parse("I didn't find any facts!").isEmpty());
            assertTrue(MemoryExtractor.parse("{\"facts\": null}").isEmpty());
            assertTrue(MemoryExtractor.parse("{\"facts\": \"none\"}").isEmpty());
            assertTrue(MemoryExtractor.parse("{}").isEmpty());
            assertTrue(MemoryExtractor.parse("[{\"predicate\":\"prefers\"}]").isEmpty());
            assertTrue(MemoryExtractor.parse("{\"facts\":[\"a string\"]}").isEmpty());
        }

        @Test
        void dropsFactsMissingTheFieldsThatMatter() {
            assertTrue(MemoryExtractor.parse(
                    "{\"facts\":[{\"subject\":\"user\",\"predicate\":\"prefers\",\"about\":\"user\"}]}")
                    .isEmpty(), "no value");
            assertTrue(MemoryExtractor.parse(
                    "{\"facts\":[{\"subject\":\"user\",\"value\":\"oak\",\"about\":\"user\"}]}")
                    .isEmpty(), "no predicate");
        }

        /** Models fence their JSON in markdown often enough that it cannot be treated as malformed. */
        @Test
        void toleratesAFencedReply() {
            List<MemoryExtractor.ExtractedFact> facts = MemoryExtractor.parse(
                    "```json\n{\"facts\":[{\"subject\":\"user\",\"predicate\":\"dislikes\","
                            + "\"value\":\"caves\",\"about\":\"user\"}]}\n```");
            assertEquals(1, facts.size());
            assertEquals("dislikes", facts.get(0).predicate());
        }
    }

    @Nested
    @DisplayName("the vocabulary is closed")
    class Vocabulary {

        @Test
        void hasExactlyTheThirteenMeasuredPredicates() {
            // The agreement numbers in the class docs were taken with these 13. Changing the set
            // invalidates them, so this asserts the count deliberately rather than incidentally.
            assertEquals(13, MemoryExtractor.PREDICATES.size());
        }

        @Test
        void dropsOutOfVocabularyPredicates() {
            // The drift the local model showed in the research run: `wants`, `likes`, `is`.
            for (String bad : new String[] {"wants", "likes", "is", "favourite_biome", "prefers_biome"}) {
                assertTrue(plan("{\"facts\":[{\"subject\":\"user\",\"predicate\":\"" + bad
                        + "\",\"value\":\"oak\",\"about\":\"user\"}]}").isEmpty(),
                        "expected '" + bad + "' to be dropped as out of vocabulary");
            }
        }

        @Test
        void acceptsEveryPredicateInTheVocabularyExceptThePolicyDrops() {
            for (String predicate : MemoryExtractor.PREDICATES) {
                List<MemoryExtractor.Candidate> got = plan("{\"facts\":[{\"subject\":\"user\","
                        + "\"predicate\":\"" + predicate + "\",\"value\":\"oak\",\"about\":\"user\"}]}");
                if (MemoryExtractor.DROPPED_PREDICATES.contains(predicate)) {
                    assertTrue(got.isEmpty(), predicate + " is a policy drop and should not survive");
                } else {
                    assertEquals(1, got.size(), predicate + " should have produced a candidate");
                }
            }
        }

        /** Dropped after extraction, not forbidden in the prompt — see the field's docs for why. */
        @Test
        void plansToIsDroppedByPolicyButStillInTheVocabulary() {
            assertTrue(MemoryExtractor.PREDICATES.contains("plans_to"));
            assertTrue(MemoryExtractor.DROPPED_PREDICATES.contains("plans_to"));
            assertTrue(plan("{\"facts\":[{\"subject\":\"user\",\"predicate\":\"plans_to\","
                    + "\"value\":\"build a castle\",\"about\":\"user\"}]}").isEmpty());
        }

        /** The prompt must not advertise a predicate the code will silently discard. */
        @Test
        void thePromptListsTheVocabulary() {
            String prompt = MemoryExtractor.systemPrompt(OWNER);
            for (String predicate : MemoryExtractor.PREDICATES) {
                assertTrue(prompt.contains(predicate), "prompt should name " + predicate);
            }
            assertTrue(prompt.contains(OWNER), "prompt should name the owner");
        }
    }

    @Nested
    @DisplayName("living things are not possessions")
    class PetsVersusItems {

        @Test
        @DisplayName("related_to keeps a pet with the player, everywhere")
        void petIsPersonScoped() {
            // Observed 2026-08-20: a real dog was stored WORLD-scoped and pinned to one save,
            // because the model reached for `owns`. related_to is the predicate that gets it right,
            // and the scope table already follows it.
            List<MemoryExtractor.Candidate> out = MemoryExtractor.plan(
                    List.of(new MemoryExtractor.ExtractedFact(
                            "user", "related_to", "a dog named duke", "user")),
                    "Dauk808", "I have a dog named Duke");
            assertEquals(1, out.size());
            assertEquals(MemoryScope.PERSON, out.get(0).scope());
        }

        @Test
        @DisplayName("owns still scopes an ITEM to the save it lives in")
        void itemStaysWorldScoped() {
            // The negative control for the fix above. Moving `owns` to PERSON to rescue the dog
            // would leak every pickaxe into every world — an invisible mistake traded for a visible
            // one. The table must keep doing this.
            List<MemoryExtractor.Candidate> out = MemoryExtractor.plan(
                    List.of(new MemoryExtractor.ExtractedFact(
                            "user", "owns", "a diamond pickaxe", "user")),
                    "Dauk808", "I have a diamond pickaxe now");
            assertEquals(1, out.size());
            assertEquals(MemoryScope.WORLD, out.get(0).scope());
        }

        @Test
        @DisplayName("the prompt still tells the model which one to use")
        void promptCarriesTheRule() {
            // The correction lives in the prompt, so the prompt is where it can be lost. Nothing
            // else in this suite would notice if that guidance were edited away.
            String prompt = MemoryExtractor.systemPrompt("Dauk808");
            assertTrue(prompt.contains("related_to"), prompt);
            assertTrue(prompt.toLowerCase().contains("pet"), "prompt no longer mentions pets");
        }
    }

    @Nested
    @DisplayName("scope is decided by table, never by the model")
    class Scope {

        @Test
        void placeAndPossessionFactsBelongToOneSave() {
            assertEquals(MemoryScope.WORLD, MemoryExtractor.scopeOf("located_in"));
            assertEquals(MemoryScope.WORLD, MemoryExtractor.scopeOf("owns"));
            assertEquals(MemoryScope.WORLD, MemoryExtractor.scopeOf("works_on"));
            assertEquals(MemoryScope.WORLD, MemoryExtractor.scopeOf("uses"));
        }

        @Test
        void factsAboutThePersonTravelWithThem() {
            assertEquals(MemoryScope.PERSON, MemoryExtractor.scopeOf("prefers"));
            assertEquals(MemoryScope.PERSON, MemoryExtractor.scopeOf("dislikes"));
            assertEquals(MemoryScope.PERSON, MemoryExtractor.scopeOf("skilled_in"));
            assertEquals(MemoryScope.PERSON, MemoryExtractor.scopeOf("has_trait"));
        }

        /**
         * The asymmetry that sets the default. A world fact misfiled as PERSON follows the player into
         * every save and is indistinguishable from a correct memory there; a person fact misfiled as
         * WORLD is merely missing. So an unknown predicate must never default to PERSON.
         */
        @Test
        void theBridgeCaseIsWorldScoped() {
            List<MemoryExtractor.Candidate> got = plan("{\"facts\":[{\"subject\":\"user\","
                    + "\"predicate\":\"works_on\",\"value\":\"a bridge over the ravine\","
                    + "\"about\":\"user\"}]}");
            assertEquals(1, got.size());
            assertEquals(MemoryScope.WORLD, got.get(0).scope());
        }
    }

    @Nested
    @DisplayName("world facts are kept, and forced to WORLD scope")
    class WorldFacts {

        /** The turn from the brief: "our base is in the taiga north of spawn". */
        @Test
        void aFactAboutTheWorldIsStoredAgainstThisSave() {
            List<MemoryExtractor.Candidate> got = plan("{\"facts\":[{\"subject\":\"the base\","
                    + "\"predicate\":\"located_in\",\"value\":\"the taiga north of spawn\","
                    + "\"about\":\"world\"}]}");
            assertEquals(1, got.size());
            assertEquals("the base is located in the taiga north of spawn", got.get(0).text());
            assertEquals(MemoryScope.WORLD, got.get(0).scope());
        }

        /**
         * The predicate table maps facts about a PERSON. A world fact whose predicate happens to be a
         * PERSON one must not inherit that, or a fact about this save goes loose in every save — the
         * one mistake here that is invisible from the outside, because a wrong-world memory reads
         * exactly like a right one.
         */
        @Test
        void aboutWorldOverridesThePredicateTable() {
            assertEquals(MemoryScope.PERSON, MemoryExtractor.scopeOf("has_trait"),
                    "precondition: has_trait is a PERSON predicate");
            List<MemoryExtractor.Candidate> got = plan("{\"facts\":[{\"subject\":\"the tower\","
                    + "\"predicate\":\"has_trait\",\"value\":\"tall\",\"about\":\"world\"}]}");
            assertEquals(1, got.size());
            assertEquals(MemoryScope.WORLD, got.get(0).scope());
        }

        /** A world fact has to name something in the world; the player is not that. */
        @Test
        void dropsAWorldFactWithNoSubjectOfItsOwn() {
            assertTrue(plan("{\"facts\":[{\"subject\":\"user\",\"predicate\":\"located_in\","
                    + "\"value\":\"the taiga\",\"about\":\"world\"}]}").isEmpty());
            assertTrue(plan("{\"facts\":[{\"subject\":\"\",\"predicate\":\"located_in\","
                    + "\"value\":\"the taiga\",\"about\":\"world\"}]}").isEmpty());
        }

        /** A world subject keeps its own name — normalisation is for the player's pronouns only. */
        @Test
        void doesNotRenameTheWorldSubjectToThePlayer() {
            List<MemoryExtractor.Candidate> got = plan("{\"facts\":[{\"subject\":\"the wheat field\","
                    + "\"predicate\":\"located_in\",\"value\":\"behind the house\",\"about\":\"world\"}]}");
            assertTrue(got.get(0).text().startsWith("the wheat field"), got.get(0).text());
            assertFalse(got.get(0).text().contains(OWNER));
        }

        @Test
        void classifiesAboutIntoTheClosedSet() {
            assertEquals(MemoryExtractor.About.PLAYER, MemoryExtractor.aboutOf(
                    new MemoryExtractor.ExtractedFact("user", "prefers", "oak", "user")));
            assertEquals(MemoryExtractor.About.WORLD, MemoryExtractor.aboutOf(
                    new MemoryExtractor.ExtractedFact("the base", "located_in", "taiga", "world")));
            assertEquals(MemoryExtractor.About.OTHER, MemoryExtractor.aboutOf(
                    new MemoryExtractor.ExtractedFact("Luna", "has_trait", "cheerful", "other")));
            assertEquals(MemoryExtractor.About.OTHER, MemoryExtractor.aboutOf(
                    new MemoryExtractor.ExtractedFact("heather", "has_trait", "patient", "heather")));
        }

        @Test
        void thePromptExplainsAllThreeAboutValues() {
            String prompt = MemoryExtractor.systemPrompt(OWNER);
            assertTrue(prompt.contains("\"user\""));
            assertTrue(prompt.contains("\"world\""));
            assertTrue(prompt.contains("\"other\""));
        }
    }

    @Nested
    @DisplayName("a fact about someone else never enters this player's store")
    class Attribution {

        /**
         * The measured failure mode: in the research corpus the low-recurrence bucket was only 31%
         * subject="user", the rest being facts about other people filed as being about the player, and
         * those misattributions accounted for most of its 11% falsehood rate.
         */
        /**
         * Note what is NOT here: a fact about the world. Those are kept and WORLD-scoped — see
         * {@link WorldFacts}. The two cases look similar and are not: a fact about another person filed
         * against this player is false, while a fact about this save is true and belongs to it.
         */
        @Test
        void dropsFactsAboutOtherPeople() {
            assertTrue(plan("{\"facts\":[{\"subject\":\"Luna\",\"predicate\":\"prefers\","
                    + "\"value\":\"oak\",\"about\":\"companion\"}]}").isEmpty());
            assertTrue(plan("{\"facts\":[{\"subject\":\"she\",\"predicate\":\"has_trait\","
                    + "\"value\":\"patient\",\"about\":\"heather\"}]}").isEmpty());
            assertTrue(plan("{\"facts\":[{\"subject\":\"Steve\",\"predicate\":\"owns\","
                    + "\"value\":\"a diamond sword\",\"about\":\"other\"}]}").isEmpty());
        }

        /** A model that omits `about` has usually still named who it meant in `subject`. */
        @Test
        void fallsBackToTheSubjectWhenAboutIsMissing() {
            assertEquals(1, plan("{\"facts\":[{\"subject\":\"user\",\"predicate\":\"prefers\","
                    + "\"value\":\"oak\"}]}").size());
            assertTrue(plan("{\"facts\":[{\"subject\":\"Luna\",\"predicate\":\"prefers\","
                    + "\"value\":\"oak\"}]}").isEmpty());
        }

        @Test
        void acceptsEveryFirstPersonFormOfTheOwner() {
            for (String self : new String[] {"user", "i", "me", "my", "we", "player", "owner"}) {
                assertEquals(1, plan("{\"facts\":[{\"subject\":\"" + self + "\",\"predicate\":\"prefers\","
                        + "\"value\":\"oak\",\"about\":\"" + self + "\"}]}").size(),
                        "expected '" + self + "' to be read as the owner");
            }
        }
    }

    @Nested
    @DisplayName("composed text names its subject")
    class Composition {

        /**
         * The bug this fixes, observed 2026-08-17: the stored text was the player's own sentence, "I
         * like to build with oak wood", and the companion read the "I" as itself — "oak wood's my
         * favorite". Composed text has no first-person pronoun to adopt.
         */
        @Test
        void firstPersonSubjectsBecomeTheOwnersName() {
            List<MemoryExtractor.Candidate> got = plan("{\"facts\":[{\"subject\":\"i\","
                    + "\"predicate\":\"prefers\",\"value\":\"oak wood\",\"about\":\"user\"}]}");
            assertEquals("Dauk808 prefers oak wood", got.get(0).text());
            assertFalse(got.get(0).text().toLowerCase().startsWith("i "),
                    "composed text must not open in the first person");
        }

        @Test
        void readsAsEnglishForEveryPredicate() {
            assertEquals("Dauk808 is working on a bridge",
                    MemoryExtractor.compose("Dauk808", "works_on", "a bridge"));
            assertEquals("Dauk808 is skilled in redstone",
                    MemoryExtractor.compose("Dauk808", "skilled_in", "redstone"));
            assertEquals("Dauk808 is patient",
                    MemoryExtractor.compose("Dauk808", "has_trait", "patient"));
            assertEquals("Dauk808 dislikes caves",
                    MemoryExtractor.compose("Dauk808", "dislikes", "caves"));
        }

        /** No predicate may compose into text containing an underscore — it would be embedded as one. */
        @Test
        void noPredicateLeaksItsUnderscoreIntoStoredText() {
            for (String predicate : MemoryExtractor.PREDICATES) {
                String text = MemoryExtractor.compose("Dauk808", predicate, "oak");
                assertFalse(text.contains("_"), predicate + " composed with an underscore: " + text);
            }
        }

        @Test
        void survivesAMissingOwnerName() {
            assertEquals("the player", MemoryExtractor.normalise("i", null));
            assertEquals("the player", MemoryExtractor.normalise("i", "  "));
        }

        /** A subject that is not the player keeps its own name, for when world facts are enabled. */
        @Test
        void leavesOtherSubjectsAlone() {
            assertEquals("the base", MemoryExtractor.normalise("the base", OWNER));
        }
    }

    @Nested
    @DisplayName("end to end, on turns from real sessions")
    class Sessions {

        /** The turn whose failure motivated extraction: the companion had no idea about the bridge. */
        @Test
        void theBridgeTurnNowProducesAMemory() {
            List<MemoryExtractor.Candidate> got = plan("{\"facts\":[{\"subject\":\"user\","
                    + "\"predicate\":\"works_on\",\"value\":\"a bridge near the ravine\","
                    + "\"about\":\"user\"}]}");
            assertEquals(1, got.size());
            assertEquals("Dauk808 is working on a bridge near the ravine", got.get(0).text());
            assertEquals(MemoryScope.WORLD, got.get(0).scope());
        }

        /** A command is not a memory, and this is what most turns look like. */
        @Test
        void anOrdinaryCommandYieldsNothing() {
            assertTrue(plan("{\"facts\":[]}").isEmpty());
        }

        @Test
        void amixedReplyKeepsOnlyWhatQualifies() {
            List<MemoryExtractor.Candidate> got = plan("{\"facts\":["
                    + "{\"subject\":\"user\",\"predicate\":\"prefers\",\"value\":\"oak wood\",\"about\":\"user\"},"
                    + "{\"subject\":\"user\",\"predicate\":\"plans_to\",\"value\":\"build a castle\",\"about\":\"user\"},"
                    + "{\"subject\":\"Luna\",\"predicate\":\"has_trait\",\"value\":\"cheerful\",\"about\":\"companion\"},"
                    + "{\"subject\":\"user\",\"predicate\":\"wants\",\"value\":\"diamonds\",\"about\":\"user\"}]}");
            assertEquals(1, got.size(), "only the in-vocabulary, non-policy-dropped, owner fact survives");
            assertEquals("Dauk808 prefers oak wood", got.get(0).text());
        }

        /** A player fact and a world fact from one exchange get different scopes, as they must. */
        @Test
        void oneExchangeCanProduceBothScopes() {
            List<MemoryExtractor.Candidate> got = plan("{\"facts\":["
                    + "{\"subject\":\"user\",\"predicate\":\"prefers\",\"value\":\"spruce\",\"about\":\"user\"},"
                    + "{\"subject\":\"the base\",\"predicate\":\"located_in\",\"value\":\"the taiga\","
                    + "\"about\":\"world\"}]}");
            assertEquals(2, got.size());
            assertEquals(MemoryScope.PERSON, got.get(0).scope());
            assertEquals(MemoryScope.WORLD, got.get(1).scope());
        }
    }

    @Nested
    @DisplayName("the companion cannot cite itself")
    class Grounding {

        private static final String SPRUCE_FACT = "{\"facts\":[{\"subject\":\"user\","
                + "\"predicate\":\"prefers\",\"value\":\"spruce for building\",\"about\":\"user\"}]}";

        /**
         * The two real turns from the 2026-08-17 session, and the whole point of the guard. The player
         * stated the preference on one and merely asked about it on the other; the companion's reply
         * supplied "spruce" both times, and without grounding the question re-confirmed the fact.
         */
        @Test
        void keepsAFactThePlayerStatedAndDropsOneOnlyTheCompanionSaid() {
            List<MemoryExtractor.ExtractedFact> facts = MemoryExtractor.parse(SPRUCE_FACT);

            assertEquals(1, MemoryExtractor.plan(facts, OWNER,
                    "did you know my favorite building material is spruce?").size(),
                    "the player said 'spruce', so it is theirs to have said");

            assertTrue(MemoryExtractor.plan(facts, OWNER,
                    "what's my favorite building material?").isEmpty(),
                    "a question states nothing; the only source of 'spruce' was the companion's reply");
        }

        /** The other real one: "duke" is in the player's words, so the pet fact stands. */
        @Test
        void keepsThePetFactFromTheTurnThatStatedIt() {
            List<MemoryExtractor.ExtractedFact> facts = MemoryExtractor.parse(
                    "{\"facts\":[{\"subject\":\"user\",\"predicate\":\"related_to\","
                            + "\"value\":\"dog named duke\",\"about\":\"user\"}]}");
            assertEquals(1, MemoryExtractor.plan(facts, OWNER,
                    "if we train a wolf we should name it duke because that's my dogs name IRL").size());
        }

        @Test
        void groundingIsDecidedOnContentWordsOnly() {
            // Function words must not ground anything, or every fact would pass on "the" or "my".
            assertFalse(MemoryExtractor.isGrounded("the oak", "what is my favorite?"));
            assertTrue(MemoryExtractor.isGrounded("oak wood", "I like oak, it looks nice"));
        }

        @Test
        void isCaseAndInflectionTolerantWhereTheTokeniserIs() {
            assertTrue(MemoryExtractor.isGrounded("Spruce", "i prefer SPRUCE"));
        }

        @Test
        void nothingIsGroundedInAnEmptyOrWordlessMessage() {
            assertFalse(MemoryExtractor.isGrounded("spruce", ""));
            assertFalse(MemoryExtractor.isGrounded("spruce", null));
            assertFalse(MemoryExtractor.isGrounded("spruce", "?!"));
        }

        /** A value with no content words of its own cannot be grounded, and is junk anyway. */
        @Test
        void aValueOfOnlyFunctionWordsIsNeverGrounded() {
            assertFalse(MemoryExtractor.isGrounded("the it", "the it is my thing"));
        }

        /** The two-argument form keeps its old meaning, so existing callers are unchanged. */
        @Test
        void groundingIsSkippedWhenNoPlayerMessageIsGiven() {
            List<MemoryExtractor.ExtractedFact> facts = MemoryExtractor.parse(SPRUCE_FACT);
            assertEquals(1, MemoryExtractor.plan(facts, OWNER).size());
            assertEquals(1, MemoryExtractor.plan(facts, OWNER, null).size());
        }

        /**
         * The acknowledged cost, asserted so it is a decision on record rather than a surprise: an
         * elliptical agreement carries a real preference and is dropped, because none of the player's
         * words name it.
         */
        @Test
        void ellipticalAgreementIsLostAndThatIsTheTrade() {
            List<MemoryExtractor.ExtractedFact> facts = MemoryExtractor.parse(SPRUCE_FACT);
            assertTrue(MemoryExtractor.plan(facts, OWNER, "yeah that one").isEmpty());
        }
    }

    @Nested
    @DisplayName("the text handed to the extractor")
    class Exchange {

        /**
         * Both halves, because a fact is often only legible in the pair — "yeah, that one" answering
         * "do you want the spruce?" carries a preference neither line holds alone.
         */
        @Test
        void includesBothSidesLabelled() {
            String text = MemoryLearner.exchange("yeah that one", "Do you want the spruce?");
            assertTrue(text.contains("The player said: yeah that one"));
            assertTrue(text.contains("The companion answered: Do you want the spruce?"));
        }

        @Test
        void survivesAMissingReply() {
            assertEquals("The player said: hello", MemoryLearner.exchange("hello", null));
            assertEquals("The player said: hello", MemoryLearner.exchange("hello", "   "));
        }
    }
}
