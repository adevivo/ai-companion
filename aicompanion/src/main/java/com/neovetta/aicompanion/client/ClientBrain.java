package com.neovetta.aicompanion.client;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import com.player2.playerengine.player2api.CompanionMemory;
import com.player2.playerengine.player2api.ConversationHistory;
import com.neovetta.aicompanion.core.EmbeddingsConfig;
import com.neovetta.aicompanion.core.LlmConfig;
import com.player2.playerengine.player2api.MemoryLearner;
import com.player2.playerengine.player2api.Player2APIService;
import com.player2.playerengine.player2api.brain.BrainWire;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.CompanionConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Doing a companion's thinking on the machine that owns it.
 *
 * <p>The server sends the ingredients of a prompt; this recalls from the <b>local</b> corpus,
 * assembles, calls the player's own model with the player's own key, learns locally, and sends back
 * only {@code {reason, command, message}}. The memories never leave this machine, and the server's
 * token bill is untouched.
 *
 * <p>Assembly happens here rather than on the server precisely because assembly is where memories
 * are injected — a server that built the prompt would have had to be handed them first.
 *
 * <h2>Never leave the companion mute</h2>
 *
 * Every failure path sends a result with an error string rather than dropping the request, so the
 * server falls back immediately instead of waiting out its timeout. The one thing this must never do
 * is stay silent.
 */
public final class ClientBrain {

    private ClientBrain() {}

    /** One service for this client, reusing the engine's retry and salvage handling. */
    private static volatile Player2APIService service;

    /**
     * Whether the server has actually asked this client to think, at least once this session.
     *
     * <p>⚠️ The only honest answer to "does the brain run here?", and it cannot be read from config.
     * {@code llm.clientBrain} in THIS file is a wish; {@link com.player2.playerengine.player2api.brain.NetworkBrainTransport#canThink}
     * evaluates the SERVER's copy, so a client with the switch off can still be the machine doing
     * every turn — which is exactly what a session on 2026-08-22 logged, reporting "brain=server" on
     * the client that was running the brain and holding the corpus. A diagnostic that confidently
     * states the opposite of what is happening is worse than none.
     */
    private static void markThinkingHere(boolean value) {
        CompanionConfig.thinkingHere = value;
    }

    public static void register() {
        // Announce that this client can think. Without it the server cannot tell a working client
        // from a vanilla one and would pay its timeout on every single turn before falling back.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // Per connection: the previous server may have delegated and this one may not.
            markThinkingHere(false);
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                    handler.registryAccess());
            // Say WHAT this client is pointed at, not merely that it exists. "Has the mod" and "can
            // actually reach a model" are different things and the server cannot tell them apart:
            // every client that gets this far has the mod, because registry sync refuses vanilla
            // ones outright. Deliberately not a gate — a client that reports a hopeless-looking
            // config is still asked to think, and if it fails the player is told so in chat. This
            // exists so an operator reading the log can SEE that a player is still on the untouched
            // default endpoint with no key, which is what a "my companion never answers" report
            // actually looks like from the server side.
            buf.writeBoolean(LlmConfig.localMode);
            buf.writeUtf(LlmConfig.baseUrl == null ? "" : LlmConfig.baseUrl);
            buf.writeBoolean(LlmConfig.apiKey != null && !LlmConfig.apiKey.isBlank());
            NetworkManager.sendToServer(BrainWire.HELLO, buf);
        });

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BrainWire.TURN_REQUEST,
                (buf, packet) -> {
                    markThinkingHere(true);
                    UUID requestId = buf.readUUID();
                    buf.readUUID(); // companion uuid — not needed here, the server tracks the turn
                    JsonObject context = BrainWire.readContext(buf);
                    // Off the render thread: this makes a network call to an LLM and may take
                    // seconds. Nothing here touches the game world.
                    CompletableFuture.runAsync(() -> think(requestId, context));
                });

        // The build planner's model call. It used to run on the server, which on a dedicated server
        // has no key, so every new build_structure failed with a 401 while conversation (already
        // routed here) worked. The server sends the finished messages; nothing here is recalled or
        // learned, because the planning prompt is about blocks, not about the player.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BrainWire.PLAN_REQUEST,
                (buf, packet) -> {
                    markThinkingHere(true);
                    UUID requestId = buf.readUUID();
                    com.google.gson.JsonArray messages = BrainWire.readPlanMessages(buf);
                    CompletableFuture.runAsync(() -> plan(requestId, messages));
                });

        // /companion remember, routed here because this machine holds the corpus. The confirmation
        // is printed from what was actually stored, and printed here — the server cannot report on a
        // write it did not make, and reporting what was submitted would claim success even when the
        // store kept an older record and dropped this one.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, BrainWire.MEMORY_REMEMBER,
                (buf, context) -> {
                    JsonObject request = BrainWire.readRemember(buf);
                    CompletableFuture.runAsync(() -> remember(request));
                });
    }

    /** Store a fact in this machine's corpus and say what was kept. */
    private static void remember(JsonObject request) {
        try {
            UUID owner = localPlayerUuid();
            if (owner == null) {
                return; // no player at the keyboard: nothing to store against, nobody to tell
            }
            String fact = BrainWire.str(request, "fact").strip();
            boolean thisWorldOnly = request.has("thisWorldOnly")
                    && request.get("thisWorldOnly").getAsBoolean();
            String worldId = BrainWire.opt(request, "worldId");
            com.neovetta.aicompanion.memory.Place place = null;
            if (request.has("place") && request.get("place").isJsonObject()) {
                JsonObject p = request.getAsJsonObject("place");
                place = new com.neovetta.aicompanion.memory.Place(
                        BrainWire.str(p, "dimension"),
                        p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("z").getAsInt());
            }

            CompanionMemory.warmForClient(owner);
            com.neovetta.aicompanion.memory.MemoryRecord saved = CompanionMemory.remember(owner, fact,
                    thisWorldOnly
                            ? com.neovetta.aicompanion.memory.MemoryScope.WORLD
                            : com.neovetta.aicompanion.memory.MemoryScope.PERSON,
                    worldId, place);
            reportMemoryHealth();
            int held = CompanionMemory.countFor(owner);
            String where = saved.place() == null ? ""
                    : "  @ " + saved.place().x() + ", " + saved.place().y() + ", " + saved.place().z();
            say(net.minecraft.network.chat.Component.literal(
                    (thisWorldOnly ? "Remembered, here in this world: " : "Remembered: ")
                            + saved.text() + where)
                    .withStyle(net.minecraft.ChatFormatting.GREEN)
                    .append(net.minecraft.network.chat.Component.literal("  (" + held + " stored on your machine)")
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY)));
        } catch (Throwable e) {
            // Throwable for the same reason think() uses it: a linkage error between mod and engine
            // arrives as an Error, and swallowing it here would leave the player staring at a command
            // that printed nothing at all.
            AiCompanion.LOGGER.warn("[{}] could not store a remembered fact", AiCompanion.MOD_ID, e);
            say(net.minecraft.network.chat.Component.literal("Could not remember that: " + e)
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    /**
     * Show this machine's memory diagnostics to the player sitting at it.
     *
     * <p>The latches are one-shot, so a broken embedder says so once and then stays quiet until it
     * recovers — and says so again when it does. Same contract as the server's drain in
     * {@code /companion reload}, just pointed at the queue that actually has anything in it.
     */
    private static void reportMemoryHealth() {
        for (com.neovetta.aicompanion.core.MemoryHealth.Notice notice
                : com.neovetta.aicompanion.core.MemoryHealth.drain()) {
            say(net.minecraft.network.chat.Component.literal("[memory] " + notice.text())
                    .withStyle(notice.problem()
                            ? net.minecraft.ChatFormatting.RED
                            : net.minecraft.ChatFormatting.GREEN));
        }
    }

    /** Put a line in this player's chat, from whatever thread happens to be running. */
    private static void say(net.minecraft.network.chat.Component text) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.displayClientMessage(text, false);
            }
        });
    }

    private static void think(UUID requestId, JsonObject context) {
        try {
            String turnText = BrainWire.opt(context, "turnText");
            String worldId = BrainWire.opt(context, "worldId");
            boolean autonomous = context.has("autonomous")
                    && context.get("autonomous").getAsBoolean();
            String companionName = BrainWire.str(context, "companionName");
            String ownerName = BrainWire.str(context, "ownerName");
            String ownerUuidRaw = BrainWire.opt(context, "ownerUuid");
            UUID ownerUuid = ownerUuidRaw == null ? localPlayerUuid() : UUID.fromString(ownerUuidRaw);

            List<JsonObject> raw = new ArrayList<>();
            JsonArray messages = context.getAsJsonArray("messages");
            for (JsonElement e : messages) {
                raw.add(e.getAsJsonObject());
            }

            // Recall from THIS machine's corpus, with the embedder's own timeout rather than the
            // server tick loop's 250 ms. Both halves of that matter and only one of them was true
            // before: there is no tick to miss here, AND there is no prefetch here either, so every
            // recall embeds cold inside whatever ceiling it is given.
            //
            // Measured on 2026-08-20: with the tick-loop budget, 2 of the 7 recalls that reached the
            // embedder were lost to "TimeoutException after 255 ms (budget 250 ms)" — one on the
            // session's first turn and one after a four-minute idle gap, both cold. Warm recalls in
            // the same session took 45-56 ms. The turns it lost are the first question after a
            // pause, which is exactly when someone is checking whether it remembers.
            List<String> memories = List.of();
            boolean playerDriven = !autonomous && ownerUuid != null
                    && turnText != null && !turnText.isBlank();
            if (playerDriven) {
                CompanionMemory.warmForClient(ownerUuid);
                memories = CompanionMemory.recall(turnText, companionName, ownerUuid, worldId,
                        EmbeddingsConfig.timeoutMs);
            }

            List<JsonObject> assembled = ConversationHistory.wrapLatest(raw,
                    BrainWire.str(context, "worldStatus"),
                    BrainWire.str(context, "agentStatus"),
                    BrainWire.str(context, "debugMessages"),
                    BrainWire.opt(context, "reminder"),
                    memories);

            JsonObject reply = service().completeConversation(ConversationHistory.of(assembled));
            send(requestId, reply == null ? null : reply.toString(), null);

            // After the answer is on its way back, never before. Extraction spends a second call and
            // must not be able to delay a reply.
            if (playerDriven && reply != null) {
                MemoryLearner.learnFrom(turnText,
                        reply.has("message") ? reply.get("message").getAsString() : null,
                        ownerUuid, ownerName, worldId, service());
            }

            // Say whatever memory found out, here, because nothing else will. MemoryHealth exists to
            // make the silent path loud, but its only drain was /companion reload — which runs on the
            // SERVER and drains the SERVER's queue. With clientBrain on, the corpus, the embedder and
            // every way they can fail are on this machine, so those notices were queued on the client
            // and read by nobody: an unreachable embedder degraded to "no memories recalled" and said
            // nothing at all. Drained after the reply for the same reason extraction runs there.
            reportMemoryHealth();
        } catch (Throwable e) {
            // Throwable: a linkage error between mod and engine arrives as an Error, and swallowing
            // it here would leave the server waiting out its timeout on every turn with nothing in
            // either log to say why.
            AiCompanion.LOGGER.warn("[{}] client brain failed at {}", AiCompanion.MOD_ID,
                    LlmConfig.baseUrl, e);
            // Name the endpoint in the error. This string is the only thing that crosses back, and
            // the server will not answer for a client that announced itself — so it is also the only
            // clue the owner gets about WHY their companion went quiet. "Connection refused" alone
            // does not say which address refused it.
            send(requestId, null, e + " (endpoint: " + LlmConfig.baseUrl + ")");
        }
    }

    /** Answer a plan request with this machine's model, or with an error the server can report. */
    private static void plan(UUID requestId, com.google.gson.JsonArray messages) {
        try {
            List<JsonObject> list = new ArrayList<>();
            for (JsonElement e : messages) {
                list.add(e.getAsJsonObject());
            }
            String text = service().completeConversationToString(ConversationHistory.of(list));
            int bytes = text == null ? 0 : text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (bytes > BrainWire.MAX_RESULT_BYTES) {
                // Too big to send back in one serverbound packet. Say so rather than letting the send
                // fail and the build wait out the timeout with no reason given.
                send(requestId, null, "the build plan was " + bytes + " bytes, over the "
                        + BrainWire.MAX_RESULT_BYTES + "-byte limit for sending it back; lower llm.maxTokens or describe a smaller build");
                return;
            }
            send(requestId, text, null);
        } catch (Throwable e) {
            AiCompanion.LOGGER.warn("[{}] build plan request failed at {}", AiCompanion.MOD_ID, LlmConfig.baseUrl, e);
            send(requestId, null, e + " (endpoint: " + LlmConfig.baseUrl + ")");
        }
    }

    private static void send(UUID requestId, String replyJson, String error) {
        try {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                    Minecraft.getInstance().level.registryAccess());
            BrainWire.writeTurnResult(buf, requestId, replyJson, error);
            NetworkManager.sendToServer(BrainWire.TURN_RESULT, buf);
        } catch (Throwable e) {
            AiCompanion.LOGGER.error("[{}] could not return a brain result; the companion will wait "
                    + "for the server's timeout", AiCompanion.MOD_ID, e);
        }
    }

    private static UUID localPlayerUuid() {
        return Minecraft.getInstance().player == null
                ? null : Minecraft.getInstance().player.getUUID();
    }

    private static Player2APIService service() {
        Player2APIService s = service;
        if (s == null) {
            synchronized (ClientBrain.class) {
                if (service == null) {
                    service = new Player2APIService(AiCompanion.MOD_ID);
                }
                s = service;
            }
        }
        return s;
    }
}
