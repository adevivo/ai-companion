package com.player2.playerengine.player2api.brain;

import com.neovetta.aicompanion.core.BrainTurnContext;
import com.neovetta.aicompanion.core.LlmConfig;
import com.neovetta.aicompanion.core.ServerPolicy;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.ConversationHistory;
import com.player2.playerengine.player2api.LLMCompleter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
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
import net.minecraft.network.RegistryFriendlyByteBuf;
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
 *       default and tell the owner — see {@link com.neovetta.aicompanion.core.ServerPolicy#serverAnswersWhenClientFails}.</li>
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

    /**
     * What the owner should actually do about their client's failure.
     *
     * <p>⚠️ This used to be one fixed sentence — "could not reach your model. Check llm.endpoint"
     * — sent whatever had gone wrong. Observed 2026-08-22: a companion hit OpenRouter's free
     * daily cap ({@code HTTP 429 … "free-models-per-day"}, {@code X-RateLimit-Limit: 50}) and its
     * owner was told to go and check an endpoint that was working perfectly. Naming the wrong
     * cause is worse than naming none: it sends someone to debug the one thing that is fine.
     *
     * <p>The client already sends its exception text back, so the answer is available and was
     * simply being dropped on the way to the player.
     *
     * <p>Every branch that sends the player to their config file also says how to apply it. Observed
     * 2026-09-23: a fresh key pasted into the file of a running game, the config screen showing that
     * new key, and three 401s in a row, because the game still held the key it read at launch.
     * "Check llm.apiKey" was correct advice and led to a file that was already right.
     */
    public static String adviseOn(String clientError) {
        String e = clientError == null ? "" : clientError.toLowerCase(java.util.Locale.ROOT);
        if (e.contains("429") || e.contains("rate limit") || e.contains("too many requests")) {
            return "your model provider is rate-limiting you. Free tiers cap requests per DAY — "
                    + "OpenRouter's is 50 without credits — and a companion spends one per turn, "
                    + "plus another per turn if memory extraction is on. Wait for the reset, add "
                    + "credits, or point llm.endpoint at a local model.";
        }
        if (e.contains("response_format")) {
            // Only reached when the server refused BOTH forms — json_object is retried as
            // json_schema automatically (Player2APIService#chatCompletion), which covers LM Studio.
            return "your model server refused the JSON Mode request. Turn off JSON Mode in the "
                    + "config screen's LLM tab (llm.useGrammar in the file), and pick a model that "
                    + "follows instructions well." + APPLY;
        }
        if (e.contains("401") || e.contains("403") || e.contains("unauthorized")
                || e.contains("invalid api key") || e.contains("no auth")) {
            return "your model provider refused the key. Check llm.apiKey in your own config, or "
                    + "the AICOMPANION_LLM_APIKEY environment variable if you set it there." + APPLY;
        }
        if (e.contains("402") || e.contains("insufficient") || e.contains("credit")
                || e.contains("quota") || e.contains("billing")) {
            return "your model provider says the account is out of credit. Top it up, or point "
                    + "llm.endpoint at a local model.";
        }
        if (e.contains(DID_NOT_ANSWER)) {
            // The server's own patience ran out, not the endpoint. Checked before the endpoint branch
            // below, which matches "timeout" too and would send the owner to a working endpoint.
            // Observed 2026-09-25: a free model took 46-57 s per build plan against a 45 s budget.
            boolean plan = e.contains("plantimeoutms");
            // Only the number is quoted: the raw reason arrives wrapped in the planner's own
            // "could not answer (...)" and reads as nested brackets in chat.
            java.util.regex.Matcher secs =
                    java.util.regex.Pattern.compile(DID_NOT_ANSWER + " (\\d+) s").matcher(e);
            String waited = secs.find() ? " of " + secs.group(1) + " s" : "";
            return "your model took longer than the server's wait" + waited + ". Big or busy"
                    + " models can need more time: raise "
                    + (plan ? "brain.planTimeoutMs" : "brain.clientTimeoutMs")
                    + " in the server's aicompanion-server.json, or "
                    + (plan ? "llm.clientPlanTimeoutMs" : "llm.clientBrainTimeoutMs")
                    + " in aicompanion.json when your own game is the host." + APPLY;
        }
        if (e.contains("could not be sent")) {
            return "the server could not send the request to your game. Rejoining usually fixes it.";
        }
        if (e.contains("503") || e.contains("overloaded") || e.contains("service unavailable")) {
            return "your model provider is overloaded right now (HTTP 503). Free models hit this"
                    + " often. Try again in a minute, or pick a less busy model.";
        }
        if (e.contains("connection refused") || e.contains("unknownhost")
                || e.contains("no route to host") || e.contains("timed out")
                || e.contains("timeout") || e.contains("connect")) {
            return "nothing answered at your endpoint. Check llm.endpoint in your own config and "
                    + "that the model server is running and reachable from your machine." + APPLY;
        }
        if (e.isBlank()) {
            return "your client did not say why. Check llm.endpoint in your own config." + APPLY;
        }
        // Say what happened rather than guessing at it — an unclassified failure is still a
        // better clue in the player's own words than a wrong diagnosis.
        return "your model returned an error — " + clientError;
    }

    /** The words every server-side timeout uses, so the advice can tell one from an endpoint fault. */
    public static final String DID_NOT_ANSWER = "did not answer within";

    /**
     * How long the server waits for a client to write a BUILD PLAN, in milliseconds. Separate from
     * {@code LlmConfig.clientBrainTimeoutMs} because a plan is a whole program, not a short JSON turn.
     * Observed 2026-09-25: a free model took 46-57 s per full-size plan, so every second-storey plan
     * hit the 45 s turn budget and was dropped although the client finished it.
     *
     * <p>Kept here rather than in the core's {@code LlmConfig}: only this line sends plans to the
     * client, and a core field would mean a core release shared with the 1.20.1 line for nothing.
     */
    public static volatile int planTimeoutMs = 180_000;

    static String timeoutReason(int ms, String key) {
        return "the client " + DID_NOT_ANSWER + " " + (ms / 1000) + " s, " + key;
    }

    /** A running game reads its config at launch, so an edit to the file changes nothing until applied. */
    static final String APPLY = " A file edit only takes effect after /companion reload, or Save in the"
            + " config screen.";

    private static final Logger LOGGER = LogManager.getLogger();

    /** Players whose client announced it can think. Cleared when they disconnect. */
    private static final Set<UUID> CAPABLE = ConcurrentHashMap.newKeySet();

    /** In-flight turns, keyed by request id. */
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    /** In-flight plain-text completions (the build planner), keyed by request id. */
    private static final Map<UUID, PendingText> PENDING_TEXT = new ConcurrentHashMap<>();

    /** One thread, daemon: only ever runs a timeout that has already failed. */
    private static final ScheduledExecutorService TIMEOUTS = newTimeoutPool();

    private final PlayerEngineController mod;

    /** What runs when the client cannot or will not. Never null. */
    private final BrainTransport fallback;

    public NetworkBrainTransport(PlayerEngineController mod, BrainTransport fallback) {
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

    /** What each capable client reported about its own LLM config, for diagnostics only. */
    private static final Map<UUID, String> CAPABILITY_DETAIL = new ConcurrentHashMap<>();

    /**
     * The owning client announced it can think. Called from the mod's packet receiver.
     *
     * <p>{@code detail} is what the client says it is pointed at. It is logged and never gated on:
     * a client is the only thing that knows whether its endpoint answers, and guessing from a URL
     * would refuse to delegate to a working setup that merely looks unusual. When a reported-looking
     * config does fail, {@code serverAnswersWhenClientFails} decides whether the player is told or
     * quietly answered from here — and the default is to tell them.
     */
    public static void markCapable(UUID player, String detail) {
        CAPABILITY_DETAIL.put(player, detail);
        if (CAPABLE.add(player)) {
            LOGGER.info("Brain: {} can think client-side ({}).", player, detail);
            warnIfServerIsThinkingAnyway(player);
        }
    }

    /**
     * The one line that would have saved an afternoon: a client said it could think and the server
     * is going to ignore it.
     *
     * <p>In game the two are indistinguishable — the companion answers either way. The differences
     * are all invisible: which machine pays, which corpus is consulted, and therefore whether the
     * companion remembers anything the player taught it somewhere else. Logged at WARN per player
     * rather than once at boot because the capability handshake is the only moment where both
     * halves of the contradiction are known at the same time.
     */
    private static void warnIfServerIsThinkingAnyway(UUID player) {
        if (LlmConfig.clientBrain && LlmConfig.localMode) {
            return;
        }
        String why = !LlmConfig.clientBrain
                ? "this server is configured to do the thinking (brain.mode=\"server\" in"
                        + " aicompanion-server.json)"
                : "llm.localMode is off on this server, which disables client-side thinking"
                        + " regardless of brain.mode";
        LOGGER.warn("Brain: {} can think client-side but this server will think for it instead — {}."
                + " Every turn will use THIS server's llm endpoint and THIS server's memory corpus,"
                + " so the player's own model and their own remembered facts are not consulted.",
                player, why);
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
        CAPABILITY_DETAIL.remove(player);
        for (Map.Entry<UUID, Pending> e : PENDING.entrySet()) {
            if (player.equals(e.getValue().owner)) {
                e.getValue().fail("the owner disconnected");
            }
        }
        for (Map.Entry<UUID, PendingText> e : PENDING_TEXT.entrySet()) {
            if (player.equals(e.getValue().owner)) {
                PENDING_TEXT.remove(e.getKey());
                e.getValue().fail("the owner disconnected");
            }
        }
    }

    /** A result came back from a client. Called from the mod's packet receiver. */
    public static void deliver(UUID requestId, String replyJson, String error) {
        PendingText text = PENDING_TEXT.remove(requestId);
        if (text != null) {
            text.complete(replyJson, error);
            return;
        }
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

    /**
     * Complete a conversation as plain text on the owner's client — for model work that is not a
     * turn, which today means the build planner.
     *
     * <p>Same rules as a turn: only a client that announced it can think is asked, exactly one of
     * reply/error/timeout acts, and a client that announced itself and then fails is NOT silently
     * answered on the operator's key unless {@link ServerPolicy#serverAnswersWhenClientFails} says so.
     *
     * @param owner the companion's owner, or null
     * @param serverFallback runs the request on this server instead; called only when policy allows
     * @return false when that client cannot take the request at all, and the caller should run it here
     */
    public static boolean completeTextOnClient(Object owner, List<JsonObject> messages,
            Consumer<String> onText, Consumer<String> onError, Runnable serverFallback) {
        if (!(owner instanceof ServerPlayer player) || !canThink(player.getUUID())
                || !NetworkManager.canPlayerReceive(player, BrainWire.PLAN_REQUEST)) {
            // Not capable, or a client from before this channel existed — sending it an unknown
            // payload risks a disconnect, so it is treated like any client that cannot think.
            return false;
        }
        UUID requestId = UUID.randomUUID();
        PendingText pending = new PendingText(requestId, player.getUUID(), onText, onError, serverFallback);
        PENDING_TEXT.put(requestId, pending);
        try {
            JsonArray array = new JsonArray();
            for (JsonObject m : messages) {
                array.add(m);
            }
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
            BrainWire.writePlanRequest(buf, requestId, array);
            NetworkManager.sendToPlayer(player, BrainWire.PLAN_REQUEST, buf);
        } catch (Throwable e) {
            PENDING_TEXT.remove(requestId);
            LOGGER.warn("Brain: could not send a plan request to {}.", player.getUUID(), e);
            pending.fail("the request could not be sent");
            return true;
        }
        int budget = planTimeoutMs;
        pending.timeout = TIMEOUTS.schedule(() -> {
            PENDING_TEXT.remove(requestId);
            pending.fail(timeoutReason(budget, "brain.planTimeoutMs"));
        }, budget, TimeUnit.MILLISECONDS);
        return true;
    }

    /** One in-flight plain-text completion, answered exactly once. */
    private static final class PendingText {
        private final UUID requestId;
        private final UUID owner;
        private final Consumer<String> onText;
        private final Consumer<String> onError;
        private final Runnable serverFallback;
        private final AtomicBoolean done = new AtomicBoolean();
        private volatile java.util.concurrent.ScheduledFuture<?> timeout;

        PendingText(UUID requestId, UUID owner, Consumer<String> onText, Consumer<String> onError,
                Runnable serverFallback) {
            this.requestId = requestId;
            this.owner = owner;
            this.onText = onText;
            this.onError = onError;
            this.serverFallback = serverFallback;
        }

        void complete(String text, String error) {
            if (!done.compareAndSet(false, true)) {
                return;
            }
            cancel();
            if (error != null && !error.isBlank()) {
                LOGGER.warn("Brain: the client could not complete a plan request ({}).", error);
                fallBackOrFail(error);
                return;
            }
            onText.accept(text == null ? "" : text);
        }

        void fail(String why) {
            if (!done.compareAndSet(false, true)) {
                return;
            }
            cancel();
            LOGGER.warn("Brain: plan request {}: {}.", requestId, why);
            fallBackOrFail(why);
        }

        private void fallBackOrFail(String why) {
            if (ServerPolicy.serverAnswersWhenClientFails) {
                LOGGER.warn("Brain: running the plan request for {} on this server's key "
                        + "(server.serverAnswersWhenClientFails=true).", owner);
                serverFallback.run();
            } else {
                // The error reaches the owner through the build's own failure notice, with this text
                // in it — so it has to say whose model failed, not just that something did.
                onError.accept("your client's model could not answer (" + why + ")");
            }
        }

        private void cancel() {
            java.util.concurrent.ScheduledFuture<?> t = this.timeout;
            if (t != null) {
                t.cancel(false);
            }
        }
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

            RegistryFriendlyByteBuf buf =
                    new RegistryFriendlyByteBuf(Unpooled.buffer(), owner.registryAccess());
            BrainWire.writeTurnRequest(buf, requestId, ctx.companionUuid(), context);
            NetworkManager.sendToPlayer(owner, BrainWire.TURN_REQUEST, buf);
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
            int budget = LlmConfig.clientBrainTimeoutMs;
            this.timeout = TIMEOUTS.schedule(
                    () -> {
                        PENDING.remove(requestId);
                        fail(timeoutReason(budget, "brain.clientTimeoutMs"));
                    },
                    budget, TimeUnit.MILLISECONDS);
        }

        void complete(String replyJson, String error) {
            if (!done.compareAndSet(false, true)) {
                return;
            }
            cancelTimeout();
            if (error != null && !error.isBlank()) {
                LOGGER.warn("Brain: the client could not think ({}).", error);
                runOnServer(error);
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
                runOnServer(null);
            }
        }

        /** Give up on the client and run the turn locally. Safe to call more than once. */
        void fail(String why) {
            if (!done.compareAndSet(false, true)) {
                return;
            }
            cancelTimeout();
            LOGGER.warn("Brain: {}.", why);
            // The reason goes through: with null, adviseOn said "your client did not say why. Check
            // llm.endpoint" after a timeout, and sent the owner to a working endpoint.
            runOnServer(why);
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
        private void runOnServer(String clientError) {
            if (!ServerPolicy.serverAnswersWhenClientFails) {
                String who = ctx.companionName() == null || ctx.companionName().isBlank()
                        ? "Your companion" : ctx.companionName();
                LOGGER.warn("Brain: {} could not think for {} and this server does not answer for "
                        + "guests (server.serverAnswersWhenClientFails=false). Turn abandoned.",
                        who, owner);
                try {
                    transport.mod.tellOwner(who + " could not think: " + adviseOn(clientError), true);
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
