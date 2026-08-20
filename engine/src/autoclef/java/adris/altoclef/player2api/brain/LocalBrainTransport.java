package adris.altoclef.player2api.brain;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.CompanionMemory;
import adris.altoclef.player2api.ConversationHistory;
import adris.altoclef.player2api.LLMCompleter;
import adris.altoclef.player2api.MemoryLearner;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.function.Consumer;

/**
 * Thinking on the game server, with the server's key and the server's copy of the memories.
 *
 * <p>This is what the mod did before {@link BrainTransport} existed, moved behind the interface and
 * otherwise unchanged. It stays the default, and it stays supported permanently: even once a client
 * can do this work, a vanilla client, an out-of-date client or one that simply does not answer has
 * to fall back to something, and freezing the companion is not an option.
 *
 * <p>⚠️ On a dedicated server this is the mode where the operator funds every player's tokens and
 * holds every player's memories on their disk. That is fine for a family LAN where the operator is
 * the parent, and it is the reason a networked transport exists at all.
 */
public final class LocalBrainTransport implements BrainTransport {

    private final AltoClefController mod;

    public LocalBrainTransport(AltoClefController mod) {
        this.mod = mod;
    }

    @Override
    public void prefetch(String turnText, java.util.UUID ownerUuid) {
        // Embeds now rather than when the turn dispatches, which is what keeps a network call off
        // the tick loop. No-op unless memory is on and the gate accepts the turn.
        CompanionMemory.prefetch(turnText, ownerUuid);
    }

    @Override
    public List<String> recall(BrainTurnContext ctx) {
        // Recall against what was actually said, and only when somebody said it. A self-prompted
        // turn ("your last command finished") has no question in it, so retrieving against the
        // InfoMessage text would rank memories about nothing and spend tokens doing it.
        if (!ctx.isPlayerDriven()) {
            return List.of();
        }
        return CompanionMemory.recall(ctx.turnText(), ctx.companionName(), ctx.ownerUuid(),
                ctx.worldId());
    }

    @Override
    public void submit(BrainTurnContext ctx, ConversationHistory prompt, LLMCompleter completer,
            Consumer<JsonObject> onReply, Consumer<String> onError) {
        completer.processToJson(mod.getPlayer2APIService(), prompt, onReply, onError);
    }

    @Override
    public void learn(BrainTurnContext ctx, String companionReply) {
        // No-op unless memory.extractionEnabled is on. Does its own work async and catches Throwable,
        // so nothing here can reach the conversation loop.
        MemoryLearner.learnFrom(ctx.turnText(), companionReply, ctx.ownerUuid(), ctx.ownerName(),
                ctx.worldId(), mod.getPlayer2APIService());
    }
}
