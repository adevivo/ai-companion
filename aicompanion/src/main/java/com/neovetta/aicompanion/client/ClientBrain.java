package com.neovetta.aicompanion.client;

import adris.altoclef.player2api.CompanionMemory;
import adris.altoclef.player2api.ConversationHistory;
import adris.altoclef.player2api.LlmConfig;
import adris.altoclef.player2api.MemoryLearner;
import adris.altoclef.player2api.Player2APIService;
import adris.altoclef.player2api.brain.BrainWire;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.neovetta.aicompanion.AiCompanion;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;

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

    public static void register() {
        // Announce that this client can think. Without it the server cannot tell a working client
        // from a vanilla one and would pay its timeout on every single turn before falling back.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            PacketByteBuf buf = PacketByteBufs.create();
            ClientPlayNetworking.send(BrainWire.HELLO, buf);
        });

        ClientPlayNetworking.registerGlobalReceiver(BrainWire.TURN_REQUEST,
                (client, handler, buf, responseSender) -> {
                    UUID requestId = buf.readUuid();
                    buf.readUuid(); // companion uuid — not needed here, the server tracks the turn
                    JsonObject context = BrainWire.readContext(buf);
                    // Off the render thread: this makes a network call to an LLM and may take
                    // seconds. Nothing here touches the game world.
                    CompletableFuture.runAsync(() -> think(requestId, context));
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

            // Recall from THIS machine's corpus. No tick loop here, so no embedding budget and no
            // prefetch: the whole reason those exist is that recall used to run on the server thread.
            List<String> memories = List.of();
            boolean playerDriven = !autonomous && ownerUuid != null
                    && turnText != null && !turnText.isBlank();
            if (playerDriven) {
                CompanionMemory.warmForClient(ownerUuid);
                memories = CompanionMemory.recall(turnText, companionName, ownerUuid, worldId);
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
        } catch (Throwable e) {
            // Throwable: a linkage error between mod and engine arrives as an Error, and swallowing
            // it here would leave the server waiting out its timeout on every turn with nothing in
            // either log to say why.
            AiCompanion.LOGGER.warn("[{}] client brain failed; the server will think instead",
                    AiCompanion.MOD_ID, e);
            send(requestId, null, String.valueOf(e));
        }
    }

    private static void send(UUID requestId, String replyJson, String error) {
        try {
            PacketByteBuf buf = PacketByteBufs.create();
            BrainWire.writeTurnResult(buf, requestId, replyJson, error);
            ClientPlayNetworking.send(BrainWire.TURN_RESULT, buf);
        } catch (Throwable e) {
            AiCompanion.LOGGER.error("[{}] could not return a brain result; the companion will wait "
                    + "for the server's timeout", AiCompanion.MOD_ID, e);
        }
    }

    private static UUID localPlayerUuid() {
        return MinecraftClient.getInstance().player == null
                ? null : MinecraftClient.getInstance().player.getUuid();
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
