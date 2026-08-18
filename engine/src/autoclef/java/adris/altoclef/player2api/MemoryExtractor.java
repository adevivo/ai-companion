package adris.altoclef.player2api;

import adris.altoclef.player2api.utils.Utils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.neovetta.aicompanion.memory.MemoryScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Turns a conversation turn into candidate memories, so the companion can learn something nobody
 * typed into a command.
 *
 * <h2>Why this exists</h2>
 *
 * Until now the only way into the store was {@code /companion remember}. The cost of that showed up
 * in a playtest on 2026-08-17: asked <em>"how are we doing with the bridge construction?"</em>, the
 * companion answered <em>"No bridge started yet"</em> — it had been helping build one and had no way
 * to know. No retrieval tuning could have answered that question, because nothing had ever been
 * written.
 *
 * <h2>The shape, and why it is this shape</h2>
 *
 * <b>The model describes; code decides.</b> It emits fields — subject, predicate, value, and who the
 * fact is about — and is never asked whether something is worth storing. Everything downstream of
 * that is a table in this file. That split is not a style preference; it is the one method that has
 * survived contact with measurement in this project, and the extraction research says so twice over:
 *
 * <ul>
 *   <li><b>Code-built keys beat model-authored keys.</b> From the same extraction call, building the
 *       key from the model's {@code subject} and {@code predicate} found 18 contradiction candidates
 *       against 11 for asking the model to emit a finished key. The model is good at identifying what
 *       a fact is about and bad at emitting the same key twice — a smoke run produced {@code kiss},
 *       {@code lips} and {@code taste} as keys, none of which would ever group with a future update.
 *   <li><b>Every hand-built salience heuristic failed.</b> Asterisk-density roleplay detection,
 *       importance filtering, a scope block, and two prompt-level durability variants — all measured
 *       against 3,095 owner verdicts, none beat baseline. What worked was a field the model already
 *       emits ({@code about}) and a genre judgement about text. Models are reliable at describing
 *       text and unreliable at adjudicating truth; only the first kind of question pays.
 * </ul>
 *
 * <h2>The controlled vocabulary</h2>
 *
 * {@link #PREDICATES} is closed, and that is measured rather than tidy. With a free-form prompt, two
 * competent models over the identical 970 turns produced predicates agreeing at Jaccard <b>0.01</b> —
 * {@code favourite_biome} and {@code prefers_biome} are the same fact scored as total disagreement.
 * The closed set moves that to <b>0.30</b> (30x) and semantic agreement from 0.641 to 0.706, which is
 * 40% of the way from random to identical rather than 27%. Compliance was 185/185 for the hosted
 * model and 93% locally, and all 13 predicates saw use with no collapse into one or two.
 *
 * <p>⚠️ <b>What the vocabulary does not fix.</b> Agreement on <em>which turns are worth extracting
 * from at all</em> went 38% → 32% — no improvement. Constraining how a fact is written cannot affect
 * whether a model thinks there is a fact there, because that judgement happens first. Two competent
 * models disagree about two thirds of the time on whether a turn holds a durable memory. That is why
 * ground truth for this component is labelled against <em>turns</em> and never against extracted
 * facts: a label pointing at a fact is invalidated by any extractor change, including a routine model
 * upgrade.
 *
 * <h2>Expect most turns to yield nothing</h2>
 *
 * 86% of turns produced no fact in the research corpus, and roughly half of what is produced in
 * ordinary conversation is <em>trivial</em> — true but volatile, like what someone is doing right
 * now. That is accepted rather than filtered, because it is a token-cost problem and not a
 * correctness one: measured against the shipped relevance-only scorer, trivia comes back at the
 * corpus base rate (14.0% against 13.4%) and <b>displaces nothing</b> — 0% of what outranks a gold
 * memory is junk. Displacement only appears once recency or importance are given weight, and those
 * terms also cost recall. See {@code docs/retrieval-scoring.md} in the memory POC.
 *
 * <p>So an empty result is the normal case and must never be treated as a failure.
 */
public final class MemoryExtractor {
    private static final Logger LOGGER = LogManager.getLogger();

    private MemoryExtractor() {}

    /**
     * The closed predicate set. Adding one is a measurement, not an edit — the agreement numbers in
     * the class docs were taken with exactly these 13.
     */
    public static final Set<String> PREDICATES = Set.of(
            "prefers", "dislikes", "owns", "uses", "works_on", "skilled_in", "interested_in",
            "located_in", "related_to", "plans_to", "has_trait", "configured_with", "experienced");

    /**
     * Predicates dropped after extraction rather than forbidden in the prompt.
     *
     * <p>{@code plans_to} is 82% junk by owner verdict — the worst of the 13, and 45 judged facts
     * yielded 8 keeps. It is dropped <b>here</b> and not in the prompt for a measured reason: every
     * prompt-level exclusion tried in this project <em>redistributed</em> volume instead of reducing
     * it. Removing 46 {@code owns} facts from the prompt raised total volume 8%, with
     * {@code has_trait} +24 and {@code works_on} +24 — the model holds an implicit quota per turn and
     * reallocates when a category is closed. Dropping after the fact cannot cause reallocation,
     * because the model is never told.
     *
     * <p>⚠️ This buys tokens, not correctness. 18% of {@code plans_to} facts were genuine keeps, and
     * since trivia was measured not to displace anything, the case for dropping them is weaker than
     * it looks. Revisit if the companion is ever caught not knowing a plan it was told about.
     */
    public static final Set<String> DROPPED_PREDICATES = Set.of("plans_to");

    /**
     * Predicates whose facts are only true in the save they were learned in.
     *
     * <p>Anything not listed is {@link MemoryScope#PERSON} — true of the player everywhere.
     *
     * <p><b>The default leans WORLD whenever a predicate is arguable, because the two mistakes cost
     * very different amounts.</b> A world fact misfiled as PERSON follows the player into every save
     * and reads exactly like a correct memory there — the leak is invisible. A person fact misfiled as
     * WORLD is merely unreachable in other saves, which is a missing memory and behaves like the
     * feature being off. So {@code owns}, {@code uses} and {@code works_on} are here: in Minecraft a
     * possession, a tool in hand and a project underway all belong to one save, even though the same
     * predicates would be biographical in the chat corpus the vocabulary was measured on.
     */
    private static final Set<String> WORLD_PREDICATES = Set.of(
            "located_in", "owns", "uses", "works_on", "configured_with");

    /** How each predicate reads in composed memory text. See {@link #compose}. */
    private static final Map<String, String> PHRASING = Map.ofEntries(
            Map.entry("prefers", "prefers"),
            Map.entry("dislikes", "dislikes"),
            Map.entry("owns", "owns"),
            Map.entry("uses", "uses"),
            Map.entry("works_on", "is working on"),
            Map.entry("skilled_in", "is skilled in"),
            Map.entry("interested_in", "is interested in"),
            Map.entry("located_in", "is located in"),
            Map.entry("related_to", "is related to"),
            Map.entry("plans_to", "plans to"),
            Map.entry("has_trait", "is"),
            Map.entry("configured_with", "is configured with"),
            Map.entry("experienced", "experienced"));

    /**
     * First-person forms rewritten to the player's name, plus the literal {@code user} the prompt
     * asks for.
     *
     * <p>Kept as a guard rather than a necessity: once the prompt demanded {@code user} explicitly,
     * 0 of 342 subjects needed rewriting. Nothing forces a model to keep obeying, and the cost of it
     * disobeying is the failure mode this whole field exists to prevent — see {@link #normalise}.
     */
    private static final Set<String> SELF_SUBJECTS = Set.of(
            "user", "i", "me", "my", "mine", "myself", "we", "us", "our", "player", "owner");

    /**
     * {@code about} values meaning "this save". Only {@code world} is asked for; the rest are the
     * near-misses a model reaches for when describing the same thing, and admitting them costs nothing
     * because scope is forced to WORLD either way.
     */
    private static final Set<String> WORLD_ABOUT = Set.of("world", "save", "game", "place", "base");

    /** One fact as the model stated it, before any code decision has been applied. */
    public record ExtractedFact(String subject, String predicate, String value, String about) {}

    /**
     * Who a fact is about, as a closed set — the same discipline as the predicate vocabulary, and for
     * the same reason.
     *
     * <p>{@code about} is the one model-emitted judgement this project measured to be worth having,
     * and leaving it free-form would put the decision "is this the player, the world, or neither" back
     * into string matching against whatever noun the model chose.
     */
    public enum About {
        /** True of the player. Scope comes from the predicate table. */
        PLAYER,
        /** True of this save — a place, a build, a structure. Always {@link MemoryScope#WORLD}. */
        WORLD,
        /** Anyone or anything else. Dropped: this store belongs to one player. */
        OTHER
    }

    /** A memory ready for {@code MemoryStore.remember}: canonical text plus the scope code chose. */
    public record Candidate(String text, MemoryScope scope, String predicate) {}

    /**
     * The extraction instructions, sent as the system prompt of a call that sees one turn.
     *
     * <p>Asks for an object rather than a bare array so the reply can satisfy
     * {@code response_format: {"type":"json_object"}} where the endpoint supports it — a top-level
     * array does not.
     */
    public static String systemPrompt(String ownerUsername) {
        return """
                You read one exchange between a Minecraft player and their AI companion, and you
                report any durable facts about the PLAYER that the exchange states.

                Return JSON of exactly this form:
                {"facts": [{"subject": "user", "predicate": "<one of the list>", "value": "<short>", "about": "user"}]}

                Rules:
                - "about" MUST be exactly one of three words:
                    "user"  — the fact is about the player, named "%s". Use "user" as the subject too.
                    "world" — the fact is about this world: a base, a build, a structure, a place.
                              Put what it is about in "subject", e.g. "the base".
                    "other" — the fact is about anyone or anything else: the companion, another
                              player, a mob, a person being talked about. Use this and it will be
                              discarded, which is correct — do not force such a fact into the other
                              two categories.
                - "predicate" MUST be exactly one of: prefers, dislikes, owns, uses, works_on,
                  skilled_in, interested_in, located_in, related_to, plans_to, has_trait,
                  configured_with, experienced. Never invent one. If nothing in the list fits, leave
                  the fact out.
                - "value" is a short noun phrase, lowercase, no punctuation. Not a sentence.
                - Report only what the exchange actually states. Do not infer, extrapolate, or fill in
                  what would probably be true.
                - Do not report what is happening right now, what was asked, or what the companion was
                  told to do. Those are not durable facts.
                - MOST EXCHANGES CONTAIN NO DURABLE FACT. Returning {"facts": []} is the correct and
                  most common answer. Never invent one to have something to say.

                Examples:
                Player: "I always build with spruce, it looks better"  ->
                {"facts": [{"subject": "user", "predicate": "prefers", "value": "spruce for building", "about": "user"}]}
                Player: "go grab me ten logs"  ->
                {"facts": []}
                Player: "our base is in the taiga north of spawn"  ->
                {"facts": [{"subject": "the base", "predicate": "located_in", "value": "the taiga north of spawn", "about": "world"}]}
                Player: "Luna is such a cheerful companion"  ->
                {"facts": [{"subject": "Luna", "predicate": "has_trait", "value": "cheerful", "about": "other"}]}
                """
                .formatted(ownerUsername);
    }

    /**
     * Reads the model's reply into facts, discarding anything malformed without throwing.
     *
     * <p>Never throws and never returns null. A turn that cannot be parsed produces no memories, which
     * is the same outcome as a turn that held no fact — and that is the common case anyway, so a parse
     * failure must not be able to break a conversation. It is logged rather than swallowed.
     */
    public static List<ExtractedFact> parse(String raw) {
        List<ExtractedFact> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        JsonObject root;
        try {
            root = Utils.parseCleanedJson(raw);
        } catch (Exception e) {
            LOGGER.warn("Memory extraction: reply was not JSON ({}). No memories from this turn. Raw=<<{}>>",
                    e.getMessage(), raw);
            return out;
        }
        JsonElement facts = root.get("facts");
        if (facts == null || !facts.isJsonArray()) {
            // A model that answers {"facts": null} or omits the key is saying "nothing here" in a
            // shape the schema did not describe. Same outcome, no need to shout about it.
            return out;
        }
        JsonArray array = facts.getAsJsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject fact = element.getAsJsonObject();
            String subject = string(fact, "subject");
            String predicate = string(fact, "predicate");
            String value = string(fact, "value");
            String about = string(fact, "about");
            if (predicate.isBlank() || value.isBlank()) {
                LOGGER.warn("Memory extraction: dropping a fact with no predicate or value: {}", fact);
                continue;
            }
            out.add(new ExtractedFact(subject, predicate.toLowerCase(Locale.ROOT).strip(), value.strip(),
                    about.toLowerCase(Locale.ROOT).strip()));
        }
        return out;
    }

    /**
     * Applies every code-side decision: what to keep, what scope it has, and how it reads.
     *
     * <p>This is where the model's output stops being advice. Nothing here consults the model again.
     *
     * @param ownerUsername the player the store belongs to, used as the subject of their own facts
     */
    public static List<Candidate> plan(List<ExtractedFact> facts, String ownerUsername) {
        return plan(facts, ownerUsername, null);
    }

    /**
     * As above, refusing any fact the player did not actually say.
     *
     * <p>See {@link #isGrounded} for why this exists. {@code playerMessage} of null skips the check,
     * which is only for callers that have already established the facts came from the player.
     */
    public static List<Candidate> plan(List<ExtractedFact> facts, String ownerUsername,
            String playerMessage) {
        List<Candidate> out = new ArrayList<>();
        if (facts == null) {
            return out;
        }
        for (ExtractedFact fact : facts) {
            if (playerMessage != null && !isGrounded(fact.value(), playerMessage)) {
                LOGGER.info("Memory extraction: dropping \"{} {}\" — the player did not say it, so the "
                        + "only source is the companion's own reply", fact.predicate(), fact.value());
                continue;
            }
            if (!PREDICATES.contains(fact.predicate())) {
                // Out of vocabulary. The local model in the research run drifted to `wants`, `likes`
                // and `is` for 7% of facts; dropping is measurable where a silent remap would not be.
                LOGGER.info("Memory extraction: dropping out-of-vocabulary predicate '{}' (value '{}')",
                        fact.predicate(), fact.value());
                continue;
            }
            if (DROPPED_PREDICATES.contains(fact.predicate())) {
                LOGGER.info("Memory extraction: dropping '{}' by predicate policy (value '{}')",
                        fact.predicate(), fact.value());
                continue;
            }
            About about = aboutOf(fact);
            if (about == About.OTHER) {
                // The measured failure mode. In the research corpus the low-recurrence bucket was only
                // 31% subject="user" — the rest carried subjects like "she" or a companion's name while
                // still being classified as about the player, and those misattributions were most of its
                // 11% falsehood rate. A fact about someone else, filed against this player, is a wrong
                // memory that reads exactly like a right one.
                LOGGER.info("Memory extraction: dropping a fact about '{}' (about={}) — this store is "
                        + "the player's", fact.subject(), fact.about());
                continue;
            }
            String subject;
            if (about == About.PLAYER) {
                subject = normalise(fact.subject(), ownerUsername);
            } else {
                // A world fact has to name the thing it is about, and that name is not the player's.
                // Normalising here would turn "the base is in the taiga" into a fact about Dauk808 —
                // the same misattribution the OTHER branch above exists to prevent, arriving by the
                // other door.
                subject = fact.subject() == null ? "" : fact.subject().strip();
                if (subject.isBlank()
                        || SELF_SUBJECTS.contains(subject.toLowerCase(Locale.ROOT))) {
                    LOGGER.info("Memory extraction: dropping a world fact whose subject is '{}' — a "
                            + "world fact must name something in the world ({} {})",
                            subject, fact.predicate(), fact.value());
                    continue;
                }
            }
            // A world fact is WORLD-scoped whatever its predicate says. The table maps predicates for
            // facts about a PERSON, and letting it decide here would file "the base is tall"
            // (has_trait) as true of the player everywhere — a world fact loose in every save, which is
            // the one mistake in this area that cannot be seen from the outside.
            MemoryScope scope = about == About.WORLD ? MemoryScope.WORLD : scopeOf(fact.predicate());
            out.add(new Candidate(compose(subject, fact.predicate(), fact.value()), scope,
                    fact.predicate()));
        }
        return out;
    }

    /**
     * Who a fact is about, read from {@code about} and falling back to {@code subject}.
     *
     * <p>The fallback matters because a model that skips the field has usually still named who it
     * meant, and the alternative — discarding every fact whose {@code about} is missing — would throw
     * away good memories over a formatting slip. Anything unrecognised is {@link About#OTHER}, so the
     * failure direction is "not stored" rather than "stored against the wrong person".
     */
    public static About aboutOf(ExtractedFact fact) {
        String about = fact.about() == null ? "" : fact.about().toLowerCase(Locale.ROOT).strip();
        if (!about.isBlank()) {
            if (SELF_SUBJECTS.contains(about)) {
                return About.PLAYER;
            }
            if (WORLD_ABOUT.contains(about)) {
                return About.WORLD;
            }
            return About.OTHER;
        }
        return SELF_SUBJECTS.contains(fact.subject() == null ? ""
                : fact.subject().toLowerCase(Locale.ROOT).strip())
                        ? About.PLAYER
                        : About.OTHER;
    }

    /**
     * Whether the player's own words support this value, rather than only the companion's reply.
     *
     * <h2>The failure this prevents</h2>
     *
     * Observed live on 2026-08-17. The player asked <em>"what's my favorite building material?"</em> —
     * a question containing no claim at all — the companion answered <em>"Spruce! You always pick
     * spruce for building"</em>, and extraction then recorded that answer as a fact and confirmed the
     * stored record with it. The companion had cited itself as evidence.
     *
     * <p>It was harmless in that instance, because the fact was true and same-day repeats do not
     * increment {@code occurrences}. The mechanism is not harmless. Confirmation is the one clean truth
     * signal the research found — keep rate 69% → 87%, false 6% → 2%, unconfounded because subject and
     * value are held fixed — and it is only meaningful while repeats are <b>independent evidence from
     * the player</b>. A companion that can confirm its own statements manufactures that evidence, and
     * the corruption is invisible: the field still reads as "stated on N separate days".
     *
     * <p>The reply still goes to the extractor, because a fact is often only legible in the pair. It is
     * context for reading the player's line, and no longer able to be the source of one.
     *
     * <h2>Why a token check rather than an instruction</h2>
     *
     * The reply is already labelled as the companion's in the prompt, and the model took it anyway.
     * Prompt-level exclusions also have a measured habit of redistributing volume rather than reducing
     * it, so a code-side check is both more reliable and incapable of that side effect.
     *
     * <p>Reuses {@link MemoryGate#tokensOf} so "did the player say this" is decided by the same
     * tokeniser and stoplist as everything else — one definition of what counts as a content word.
     *
     * <p>⚠️ <b>The cost is elliptical statements.</b> <em>"yeah, that one"</em> answering <em>"do you
     * want the spruce?"</em> carries a real preference and will now be dropped, because none of the
     * player's words name it. That is a deliberate trade: losing a fact leaves the companion as it was,
     * while a self-confirming loop degrades a signal that other decisions rest on.
     */
    public static boolean isGrounded(String value, String playerMessage) {
        Set<String> said = MemoryGate.tokensOf(playerMessage);
        if (said.isEmpty()) {
            return false;
        }
        for (String token : MemoryGate.tokensOf(value)) {
            if (said.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a fact with this predicate is true everywhere or only in one save. */
    public static MemoryScope scopeOf(String predicate) {
        return WORLD_PREDICATES.contains(predicate) ? MemoryScope.WORLD : MemoryScope.PERSON;
    }

    /**
     * Rewrites a first-person subject to the player's name.
     *
     * <p>The reason this is not cosmetic: a memory stored as <em>"I like to build with oak wood"</em>
     * is in the player's voice, and when the companion reads its own context it reads that "I" as
     * itself. Observed on 2026-08-17 — asked how the player felt about dirt structures, with the
     * reasoning field correctly saying <em>"Owner asked how THEY feel"</em>, the reply came out as
     * <em>"oak wood's MY favorite"</em>. The stored text was the player's sentence verbatim and the
     * companion adopted it. Naming the subject removes the pronoun that made that possible.
     */
    public static String normalise(String subject, String ownerUsername) {
        String owner = ownerUsername == null || ownerUsername.isBlank() ? "the player" : ownerUsername;
        if (subject == null || subject.isBlank()) {
            return owner;
        }
        return SELF_SUBJECTS.contains(subject.toLowerCase(Locale.ROOT).strip()) ? owner : subject.strip();
    }

    /**
     * Builds the sentence that gets stored and embedded.
     *
     * <p>Composed in code rather than taken from the model, which keeps the entity key clustered on
     * fact text — the scheme the research settled on. Keying on {@code subject + predicate} was
     * measured to be far too coarse under a closed vocabulary: 1,462 facts about one player collapsed
     * into 30 keys with the largest holding 244, so {@code user/prefers} would group "prefers oak" with
     * "prefers the plains biome" and let one supersede the other. A wrong supersession is worse than a
     * missed one, because it destroys a memory that was true.
     */
    public static String compose(String subject, String predicate, String value) {
        String phrase = PHRASING.getOrDefault(predicate, predicate.replace('_', ' '));
        return (subject + " " + phrase + " " + value).strip();
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }
}
