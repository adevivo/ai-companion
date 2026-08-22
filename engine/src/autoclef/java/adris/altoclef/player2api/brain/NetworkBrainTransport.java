package adris.altoclef.player2api.brain;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.ConversationHistory;
import adris.altoclef.player2api.LLMCompleter;
import adris.altoclef.player2api.LlmConfig;
import adris.altoclef.player2api.ServerPolicy;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Thinking on the owning player's client, with their key and their memories.
 *
 * <p>The server sends the ingredients of a prompt; the client recalls from its own corpus, assembles,
 * calls its own model, learns locally, and sends back only {@code {reason, command, message}}. The
 * player's memories never reach the server — which is the point, and is why assembly moves too. A
 * server that assembled the prompt would have to be handed the memories to put in it.
 *
 * <h2>Falling back is normal operation — but only for a client that never claimed otherwise</h2>
 *
 * ⚠️ There are two different situations here and they have opposite answers, because the difference
 * is whose money it is.
 *
 * <ul>
 *   <li>A client that <b>never announced</b> — vanilla, out of date, or {@code clientBrain} off — was
 *       always the server's to think for. That is the operator's own configuration. Answer it.</li>
 *   <li>A client that <b>announced and then failed</b> has its own brain and its own key; it is
 *       simply broken. Answering costs the <em>operator</em> for a guest's misconfiguration, every
 *       turn, for as long as it stays broken, and neither of them can see it happening. Refuse by
 *       default and tell the owner — see {@link adris.altoclef.player2api.ServerPolicy#serverAnswersWhenClientFails}.</li>
 * </ul>
 *
 * <p>The capability handshake is what separates the two, which is a second job for a mechanism that
 * already had to exist.
 *
 * <p>A vanilla client, an out-of-date one, or one that goes quiet must never leave a companion mute.
 * Two mechanisms, and both are needed:
 *
 * <ul>
 *   <li><b>The handshake.</b> Only a client that announced itself is asked. Without this the server
 *       could not tell "still thinking" from "will never answer", and would pay the timeout on every
 *       turn for every vanilla player — a companion late by the timeout on every reply is worse than
 *       one that never left the server.</li>
 *   <li><b>The timeout.</b> For a client that announced itself and then stopped answering. Generous,
 *       because nothing is blocked while it runs.</li>
 * </ul>
 *
 * <p>⚠️ Exactly one of reply/error/timeout may act on a turn. {@code AgentConversationData} clears
 * {@code isProcessing} in its callbacks, so a late reply arriving after a timeout has already fallen
 * back would run a second turn against a conversation that has moved on. {@link Pending#done} is the
 * latch that prevents it.
 */
public final class NetworkBrainTransport implements BrainTransport {
    private static final Logger LOGGER = LogManager.getLogger();

    /** Players whose client announced it can think. Cleared when they disconnect. */
    private static final Set<UUID> CAPABLE = ConcurrentHashMap.newKeySet();

    /** In-flight turns, keyed by request id. */
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    /** One thread, daemon: only ever runs a timeout that has already failed. */
    private static final ScheduledExecutorService TIMEOUTS = newTimeoutPool();

    private final AltoClefController mod;

    /** What runs when the client cannot or will not. Never null. */
    private final BrainTransport fallback;

    public NetworkBrainTransport(AltoClefController mod, BrainTransport fallback) {
        this.mod = mod;
        this.fallback = fallback;
    }

    private static ScheduledExecutorService newTimeoutPool() {
        ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "aicompanion-brain-timeout");
            t.setDaemon(true);
            return t;
        });
        p.setRemoveOnCancelPolicy(true);
        return p;
    }

    /** The owning client announced it can think. Called from the mod's packet receiver. */
    public static void markCapable(UUID player) {
        if (CAPABLE.add(player)) {
            LOGGER.info("Brain: {} can think client-side.", player);
        }
    }

    /**
     * They left. Drop the capability and fail anything of theirs still in flight.
     *
     * <p>Without the second half, a player quitting mid-turn leaves a request that nobody will ever
     * answer and a companion stuck in {@code isProcessing} until the timeout — and on a reconnect the
     * same companion is reused, so it would appear permanently mute rather than briefly late.
     */
    public static void forget(UUID player) {
        CAPABLE.remove(player);
        for (Map.Entry<UUID, Pending> e : PENDING.entrySet()) {
            if (player.equals(e.getValue().owner)) {
                e.getValue().fail("the owner disconnected");
            }
        }
    }

    /** A result came back from a client. Called from the mod's packet receiver. */
    public static void deliver(UUID requestId, String replyJson, String error) {
        Pending p = PENDING.remove(requestId);
        if (p == null) {
            // Already timed out and fell back, or arrived twice. Dropping it is correct: the turn it
            // belonged to has been answered by other means and is no longer waiting.
            LOGGER.info("Brain: dropping a late or duplicate result for {}.", requestId);
            return;
        }
        p.complete(replyJson, error);
    }

    /**
     * Whether this player's client does its own thinking, and therefore holds its own corpus.
     *
     * <p>Player-scoped rather than companion-scoped, deliberately. A companion's turn is the common
     * case, but {@code /companion remember} is a per-player act that must work with no companion
     * spawned at all — and routing it through a companion's transport would fall back to the server
     * in exactly that case, which is the bug it exists to fix: memories split across two machines,
     * with a recall that finds nothing and says nothing.
     *
     * <p>One definition, used by both, so the two can never disagree about where a player's memories
     * live.
     */
    public static boolean canThink(UUID player) {
        return LlmConfig.clientBrain && LlmConfig.localMode
                && player != null && CAPABLE.contains(player);
    }

    /** Whether this companion's owner is online and able to think for it right now. */
    private ServerPlayer thinkingOwner() {
        if (!(mod.getOwner() instanceof ServerPlayer owner)) {
            return null;
        }
        return canThink(owner.getUUID()) ? owner : null;
    }

    @Override
    public void prefetch(String turnText, UUID ownerUuid) {
        // Deliberately nothing when the client is thinking. Prefetch exists because recall() runs on
        // the server tick loop and cannot wait; on the client it is not on a tick loop at all, so it
        // can simply take the time it needs. The head start is not needed, and asking for one would
        // mean a second round trip per turn to save nothing.
        if (thinkingOwner() == null) {
            fallback.prefetch(turnText, ownerUuid);
        }
    }

    @Override
    public List<String> recall(BrainTurnContext ctx) {
        // The client recalls from its own corpus as part of assembling the prompt, so there is
        // nothing for the server to contribute and nothing for it to see.
        return thinkingOwner() == null ? fallback.recall(ctx) : List.of();
    }

    @Override
    public void learn(BrainTurnContext ctx, String companionReply) {
        // Same: extraction runs on the client, against the client's store, with the client's key.
        if (thinkingOwner() == null) {
            fallback.learn(ctx, companionReply);
        }
    }

    @Override
    public void submit(BrainTurnContext ctx, ConversationHistory prompt, LLMCompleter completer,
            Consumer<JsonObject> onReply, Consumer<String> onError) {
        ServerPlayer owner = thinkingOwner();
        if (owner == null) {
            fallback.submit(ctx, prompt, completer, onReply, onError);
            return;
        }

        UUID requestId = UUID.randomUUID();
        Pending pending = new Pending(requestId, owner.getUUID(), ctx, prompt, completer,
                onReply, onError, this);
        PENDING.put(requestId, pending);

        try {
            // The RAW history, not the assembled prompt: the client injects its own memories while
            // assembling, and a prompt built here would have needed them handed to the server first.
            JsonArray messages = new JsonArray();
            for (JsonObject m : ctx.rawMessages()) {
                messages.add(m);
            }
            JsonObject context = BrainWire.context(messages, ctx.worldStatus(), ctx.agentStatus(),
                    ctx.debugMessages(), ctx.reminder(), ctx.turnText(), ctx.worldId(),
                    ctx.companionName(), ctx.ownerName(),
                    ctx.ownerUuid() == null ? null : ctx.ownerUuid().toString(), ctx.autonomous());

            FriendlyByteBuf buf = PacketByteBufs.create();
            BrainWire.writeTurnRequest(buf, requestId, ctx.companionUuid(), context);
            ServerPlayNetworking.send(owner, BrainWire.TURN_REQUEST, buf);
        } catch (Throwable e) {
            // Could not even send it. Decide now rather than waiting out a timeout for a packet that
            // never left — and decide it in the one place that knows who pays, rather than reaching
            // for the fallback directly and quietly billing the operator.
            PENDING.remove(requestId);
            LOGGER.warn("Brain: could not send a turn to {}.", owner.getUUID(), e);
            pending.fail("the turn could not be sent");
            return;
        }

        pending.armTimeout();
    }

    /** One in-flight turn, and the latch that guarantees it is answered exactly once. */
    private static final class Pending {
        private final UUID requestId;
        private final UUID owner;
        private final BrainTurnContext ctx;
        private final ConversationHistory prompt;
        private final LLMCompleter completer;
        private final Consumer<JsonObject> onReply;
        private final Consumer<String> onError;
        private final NetworkBrainTransport transport;
        private final AtomicBoolean done = new AtomicBoolean();
        private volatile java.util.concurrent.ScheduledFuture<?> timeout;

        Pending(UUID requestId, UUID owner, BrainTurnContext ctx, ConversationHistory prompt,
                LLMCompleter completer, Consumer<JsonObject> onReply, Consumer<String> onError,
                NetworkBrainTransport transport) {
            this.requestId = requestId;
            this.owner = owner;
            this.ctx = ctx;
            this.prompt = prompt;
            this.completer = completer;
            this.onReply = onReply;
            this.onError = onError;
            this.transport = transport;
        }

        void armTimeout() {
            this.timeout = TIMEOUTS.schedule(
                    () -> {
                        PENDING.remove(requestId);
                        fail("the client did not answer within " + LlmConfig.clientBrainTimeoutMs
                                + " ms");
                    },
                    LlmConfig.clientBrainTimeoutMs, TimeUnit.MILLISECONDS);
        }

        void complete(String replyJson, String error) {
            if (!done.compareAndSet(false, true)) {
                return;
            }
            cancelTimeout();
            if (error != null && !error.isBlank()) {
                LOGGER.warn("Brain: the client could not think ({}).", error);
                runOnServer();
                return;
            }
            try {
                JsonObject reply = com.google.gson.JsonParser.parseString(replyJson).getAsJsonObject();
                onReply.accept(reply);
            } catch (Throwable e) {
                // A malformed reply from a client is not the same as a malformed reply from a model:
                // it means the client is broken or hostile, and re-running the turn on the server is
                // the honest response rather than feeding garbage into the conversation.
                LOGGER.warn("Brain: unparseable result from the client; thinking on the server "
                        + "instead. Raw was <<{}>>", replyJson, e);
                runOnServer();
            }
        }

        /** Give up on the client and run the turn locally. Safe to call more than once. */
        void fail(String why) {
            if (!done.compareAndSet(false, true)) {
                return;
            }
            cancelTimeout();
            LOGGER.warn("Brain: {}.", why);
            runOnServer();
        }

        /**
         * The client announced it could think and then could not. Decide who pays.
         *
         * <p>⚠️ By default, nobody. See {@link ServerPolicy#serverAnswersWhenClientFails}: answering
         * here spends the <em>operator's</em> key on a guest's broken endpoint, silently, on every
         * turn, for as long as it stays broken. The player sees working replies and has no reason to
         * fix anything; the operator sees a bill and no cause.
         *
         * <p>Refusing must not make the companion mute, though — {@code AgentSideEffects.onError}
         * only writes to the log, so the owner would get nothing at all. So the owner is told
         * directly, in words they can act on, and the turn is completed as an error so the
         * conversation does not sit in {@code isProcessing} for ever.
         */
        private void runOnServer() {
            if (!ServerPolicy.serverAnswersWhenClientFails) {
                String who = ctx.companionName() == null || ctx.companionName().isBlank()
                        ? "Your companion" : ctx.companionName();
                LOGGER.warn("Brain: {} could not think for {} and this server does not answer for "
                        + "guests (server.serverAnswersWhenClientFails=false). Turn abandoned.",
                        who, owner);
                try {
                    transport.mod.tellOwner(who + " could not reach your model. Check llm.endpoint "
                            + "in your own config — this server does not think for you.", true);
                } catch (Throwable ignored) {
                    // Owner offline, or mid-teardown. The error below still frees the conversation.
                }
                onError.accept("client brain unavailable and the server does not answer for guests");
                return;
            }
            LOGGER.warn("Brain: answering for {} on this server's key "
                    + "(server.serverAnswersWhenClientFails=true).", owner);
            try {
                // Recall was skipped on the way out, because the client was expected to do it. The
                // fallback prompt therefore has no memories in it — correct rather than ideal: a
                // turn answered without memory is how the companion behaves with the feature off,
                // and re-assembling the prompt here would mean rebuilding it from a history that has
                // already moved on.
                transport.fallback.submit(ctx, prompt, completer, onReply, onError);
            } catch (Throwable e) {
                LOGGER.error("Brain: server-side fallback failed too; the turn is lost.", e);
                onError.accept("brain fallback failed: " + e);
            }
        }

        private void cancelTimeout() {
            java.util.concurrent.ScheduledFuture<?> t = this.timeout;
            if (t != null) {
                t.cancel(false);
            }
        }
    }
}
