package adris.altoclef.player2api;

import com.neovetta.aicompanion.memory.MemoryRecord;
import com.neovetta.aicompanion.memory.MemoryScope;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Runs extraction over a finished turn and writes whatever survives {@link MemoryExtractor}.
 *
 * <h2>Why this is a separate class from the extractor</h2>
 *
 * {@link MemoryExtractor} is pure logic — a prompt, a parser, and a set of tables — so it is entirely
 * unit-testable and knows nothing about threads, networks or stores. This class is the part that
 * cannot be tested that way: it makes a paid call, touches a store, and runs after a conversation turn
 * has already gone out. Keeping them apart is what lets every decision in extraction have a test while
 * the untestable half stays small enough to read.
 *
 * <h2>Where it runs, and what it must never do</h2>
 *
 * <b>After the reply is sent.</b> The player already has their answer before this starts, so it can
 * never delay a turn no matter how slow the endpoint is. It runs on the common pool, off both the
 * server thread and the conversation's own completion thread.
 *
 * <p><b>It must never be able to break a turn.</b> Every failure — a dead endpoint, malformed output,
 * a spend cap, an embedder that has gone away — has to end as "no memories from that turn", which is
 * also what a successful extraction looks like 86% of the time. So the whole thing is wrapped in a
 * catch of {@link Throwable}, and for the same reason {@code CompanionMemory.loadFor} is: a version
 * skew between this engine and the memory jar arrives as an {@link Error}, not an exception, and one
 * already vanished silently inside an unobserved {@link CompletableFuture} once. The symptom was a
 * companion that never recalled anything, with nothing in the log at all.
 */
public final class MemoryLearner {
    private static final Logger LOGGER = LogManager.getLogger();

    private MemoryLearner() {}

    /**
     * Extracts durable facts from one exchange and stores them, off the critical path.
     *
     * <p>Returns immediately. Every argument is resolved by the caller on whatever thread it is on,
     * because {@code WorldIdentity} touches {@code SavedData} and must not be read from here.
     *
     * @param playerMessage what the player said
     * @param companionReply what the companion answered, for the context that makes a fact legible
     * @param player        whose store to write to
     * @param ownerUsername the player's name, used as the subject of their own facts
     * @param worldId       the current save's id, required for anything WORLD-scoped; a null makes
     *                      world facts unstorable and they are skipped rather than mis-scoped
     * @param service       the LLM client to spend a call on
     */
    public static void learnFrom(String playerMessage, String companionReply, UUID player,
            String ownerUsername, String worldId, Player2APIService service) {
        if (!MemoryConfig.enabled || !MemoryConfig.extractionEnabled) {
            return;
        }
        if (playerMessage == null || playerMessage.isBlank()) {
            // Ordinary and frequent: a self-prompted turn has no player line to learn from. Silent on
            // purpose — logging it would put a line under every autonomous turn and drown the ones
            // below that mean something.
            return;
        }
        if (player == null || service == null) {
            // Neither of these should happen on a turn that had a player message, and both would
            // present as extraction that simply never runs.
            LOGGER.warn("Memory extraction: not running — {}. Nothing will be learned this turn.",
                    player == null ? "no owner resolved" : "no LLM service");
            return;
        }
        // The same gate retrieval uses, for the same reason and at a different cost: there is nothing
        // durable to learn from "hi" or "attack that zombie", and here a wasted turn is a paid API call
        // rather than a wasted embedding. Note it runs WITHOUT store tokens on purpose — the
        // store-aware route exists to let a turn ask about something already known, which is a reason
        // to retrieve and not a reason to extract.
        if (!MemoryGate.admits(playerMessage)) {
            // INFO, not debug. At debug this produced NO output on a server logging at INFO, so a
            // turn refused here left no trace at any prefix — and on 2026-08-18 the one turn of four
            // that actually held a durable fact ("he's a brown dog; pitbull lab mix…") was refused
            // for having no first- or second-person pronoun, silently. The absence of a line read as
            // "extraction ran and found nothing", which wants the opposite fix. One line per skipped
            // turn is the cheapest possible price for telling those two apart.
            LOGGER.info("Memory extraction: skipping this turn ({}) — turn was: \"{}\"",
                    MemoryGate.explain(playerMessage), abbreviate(playerMessage));
            return;
        }
        CompletableFuture.runAsync(() ->
                extract(playerMessage, companionReply, player, ownerUsername, worldId, service));
    }

    private static void extract(String playerMessage, String companionReply, UUID player,
            String ownerUsername, String worldId, Player2APIService service) {
        try {
            ConversationHistory history =
                    new ConversationHistory(MemoryExtractor.systemPrompt(ownerUsername));
            history.addUserMessage(exchange(playerMessage, companionReply), service);

            String raw = service.completeDeterministicJson(history);
            List<MemoryExtractor.ExtractedFact> facts = MemoryExtractor.parse(raw);
            // Grounded against the player's line only. The reply was sent to the extractor as context
            // and must not be able to become the source of a fact — see MemoryExtractor.isGrounded.
            List<MemoryExtractor.Candidate> candidates =
                    MemoryExtractor.plan(facts, ownerUsername, playerMessage);
            // Reported here, before the candidates are weighed: the call came back and parsed, which
            // is the whole of what health tracks. Extraction learning NOTHING is the normal outcome
            // on 86% of turns, and counting that as a failure would have the companion announce that
            // it had stopped learning while it was working exactly as designed.
            MemoryHealth.extractionSucceeded();
            if (candidates.isEmpty()) {
                // Logged at INFO even though it is the common case, because the alternative is being
                // unable to tell "the model found nothing" from "extraction never ran" — and this
                // project has now been bitten three times by a silent path that looked like a working
                // one. It is one line per turn.
                //
                // The raw reply comes too, truncated. "0 extracted" alone cannot distinguish a model
                // that answered {"facts": []} from one that answered something the parser threw away,
                // and those want completely different fixes.
                LOGGER.info("Memory: learned nothing from this turn ({} extracted, none kept). "
                                + "Extractor replied: {}",
                        facts.size(), abbreviate(raw));
                return;
            }

            int stored = 0;
            for (MemoryExtractor.Candidate candidate : candidates) {
                if (candidate.scope() == MemoryScope.WORLD && worldId == null) {
                    // Never fall back to PERSON to get it written. A world fact loose in every save
                    // reads exactly like a correct memory there, which is the one failure in this area
                    // that cannot be seen from the outside.
                    LOGGER.warn("Memory: not storing world fact \"{}\" — no world id was resolved for "
                            + "this turn, and PERSON would leak it into every save.", candidate.text());
                    continue;
                }
                try {
                    MemoryRecord record = CompanionMemory.remember(player, candidate.text(),
                            candidate.scope(), candidate.scope() == MemoryScope.WORLD ? worldId : null,
                            null, CompanionMemory.SOURCE_EXTRACTOR);
                    stored++;
                    LOGGER.info("Memory: learned \"{}\" [{} {}] from conversation",
                            record.text(), record.scope(), candidate.predicate());
                } catch (Throwable e) {
                    // One bad fact must not cost the others.
                    LOGGER.warn("Memory: could not store learned fact \"{}\" ({})",
                            candidate.text(), e.toString());
                }
            }
            if (stored > 0) {
                LOGGER.info("Memory: extraction stored {} of {} candidate(s) from this turn",
                        stored, candidates.size());
            }
        } catch (Throwable e) {
            LOGGER.warn("Memory: extraction failed for this turn; nothing was learned. "
                    + "The conversation is unaffected.", e);
            // Counted rather than announced: one failed call costs one turn's worth of facts, most
            // of which would have been nothing. A run of them means the player is paying for a
            // feature that has quietly stopped running, which they should hear about.
            MemoryHealth.extractionFailed(e);
        }
    }

    /** Enough of a string to recognise it in a log, without wrapping the console. */
    private static String abbreviate(String text) {
        if (text == null) {
            return "<null>";
        }
        String flat = text.replace('\n', ' ').strip();
        return flat.length() <= 300 ? flat : flat.substring(0, 299) + "…";
    }

    /**
     * The text the extractor reads.
     *
     * <p>Both halves, because a fact is often only legible in the pair: <em>"yeah, that one"</em>
     * answering <em>"do you want the spruce?"</em> carries a preference that neither line holds alone.
     * The reply is a model's own output rather than something the player said, so it is labelled as
     * such — a fact the companion invented and then read back as memory would be a closed loop with no
     * one in it.
     */
    static String exchange(String playerMessage, String companionReply) {
        StringBuilder sb = new StringBuilder();
        sb.append("The player said: ").append(playerMessage.strip());
        if (companionReply != null && !companionReply.isBlank()) {
            sb.append("\nThe companion answered: ").append(companionReply.strip());
        }
        return sb.toString();
    }
}
