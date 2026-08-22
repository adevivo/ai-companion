package adris.altoclef.player2api.brain;

import adris.altoclef.player2api.ConversationHistory;
import adris.altoclef.player2api.LLMCompleter;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.function.Consumer;

/**
 * Where a companion's thinking happens — and, with it, whose key pays for it and whose memories it
 * reads.
 *
 * <h2>Why this boundary exists</h2>
 *
 * Every one of these three operations touches something that belongs to <b>one player</b>: their LLM
 * credential, their stored memories, their embedder. All three currently run on the game server,
 * which on a dedicated server means the operator funds everyone's tokens and holds everyone's
 * memories. Neither is acceptable beyond a family LAN, and the fix for both is the same — move these
 * three calls to the owning client.
 *
 * <p>This interface is that move, expressed first as a seam. {@link LocalBrainTransport} does
 * exactly what the code did before it existed, so introducing it changes nothing; a networked
 * implementation later changes where the work happens without touching the conversation loop.
 *
 * <h2>Three methods, because they have three different threading stories</h2>
 *
 * They are deliberately not collapsed into one call. {@link #recall} runs on the server thread under
 * a hard budget, {@link #submit} is fully async, and {@link #learn} runs after the player already
 * has their answer. A single method would have to pick one of those and would be wrong twice.
 */
public interface BrainTransport {

    /**
     * Start whatever work makes {@link #recall} cheap, as soon as a message is queued.
     *
     * <p>Returns immediately and is called at least a tick before the turn dispatches. It exists
     * because {@link #recall} runs on the server thread: locally that means embedding the turn text
     * ahead of time so the vector is already waiting, and over a network it is where the round trip
     * to the client has to happen, since a tick cannot wait for one.
     *
     * <p>⚠️ Must gate identically to {@link #recall}. A prefetch that admitted a different set of
     * turns would quietly cost recall its head start — the turn would be admitted later with nothing
     * waiting, and pay the full cost inside the budget.
     *
     * @param ownerUuid the owner, because the gate consults what they have stored
     */
    void prefetch(String turnText, java.util.UUID ownerUuid);

    /**
     * Memories worth putting in this turn's prompt, best first, or empty.
     *
     * <p>⚠️ <b>Called on the SERVER THREAD.</b> It must not make an unbounded network call. The
     * local implementation is bounded by {@code MemoryConfig.embedBudgetMs} and relies on the
     * embedding having been prefetched when the message was queued.
     *
     * <p>⚠️ This is the method that does not survive being moved to a client unchanged: a packet
     * round trip cannot happen inside a server tick. A networked implementation has to prefetch
     * against the client at queue time, exactly as {@code CompanionMemory.prefetch} already does
     * against the embedder, and this signature is where that problem becomes visible instead of
     * being discovered late.
     *
     * <p>Never throws. Every failure degrades to "no memories", which is how the companion behaves
     * with the feature switched off.
     */
    List<String> recall(BrainTurnContext ctx);

    /**
     * Run the model against an assembled prompt and hand back its JSON reply.
     *
     * <p>Returns immediately; exactly one of {@code onReply} or {@code onError} is invoked later,
     * on an unspecified thread.
     *
     * @param completer the pooled completer chosen for this turn. A networked implementation ignores
     *                  it — the point of moving the brain is that the server stops making this call
     *                  at all — but it stays in the signature because the local path is a real,
     *                  supported mode and not a temporary scaffold: it is the fallback whenever the
     *                  owning client cannot answer.
     */
    void submit(BrainTurnContext ctx, ConversationHistory prompt, LLMCompleter completer,
            Consumer<JsonObject> onReply, Consumer<String> onError);

    /**
     * Learn whatever the finished exchange holds, off the critical path.
     *
     * <p>Returns immediately and must never be able to delay or break a turn. Called after the reply
     * has gone out, so a slow or dead extractor costs nothing.
     *
     * @param companionReply what the companion answered, for the context that makes a fact legible
     */
    void learn(BrainTurnContext ctx, String companionReply);
}
