package com.neovetta.aicompanion.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.ClientProfiles;
import com.neovetta.aicompanion.CompanionConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The two halves of configuration that have to cross the wire.
 *
 * <p>Out: who this player's companions are, and whether their chat is prefixed. Both are theirs to
 * choose and neither is theirs to act on — the server spawns the companion and the server routes the
 * chat — so they are announced at join.
 *
 * <p>In: the operator's rules, so the Server tab of the config screen can show what is actually
 * enforced instead of the client's own inert copy of the same keys. Display only; nothing on this
 * side acts on them.
 *
 * <p>Sending fails silently by design. A vanilla server, or one running an older version, has no
 * receiver registered for these channels — that is the ordinary case, not an error, and it must not
 * produce a stack trace on every join.
 */
public final class ClientConfigSync {

    private ClientConfigSync() {}

    /** The last policy this client was told about, or null when connected to a server without one. */
    private static volatile JsonObject serverPolicy;

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> announce());

        // Leaving clears it, so the config screen never shows the previous server's rules as though
        // they were this one's — the failure mode a stale cache always has.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> serverPolicy = null);

        ClientPlayNetworking.registerGlobalReceiver(AiCompanion.SERVER_POLICY,
                (client, handler, buf, responseSender) -> {
                    String json = new String(buf.readByteArray(), StandardCharsets.UTF_8);
                    try {
                        serverPolicy = JsonParser.parseString(json).getAsJsonObject();
                    } catch (Exception e) {
                        AiCompanion.LOGGER.warn("[{}] unreadable server policy ({})",
                                AiCompanion.MOD_ID, e.toString());
                    }
                });
    }

    /**
     * Re-read this machine's config and apply the half that belongs to this player, then say so.
     *
     * <p>Driven by {@code /companion reload} via {@link AiCompanion#RELOAD_CLIENT_CONFIG}. The
     * server's own reload cannot do this job on a dedicated server: the settings that decide where
     * this client thinks, embeds and fetches audio are read from the file sitting on this disk, and
     * nothing on the server has ever seen it.
     *
     * <p>The roster is re-announced afterwards because reload is exactly when it may have changed —
     * a hand-edited {@code companions} block is otherwise invisible to the server until reconnect.
     *
     * <p>Memory's latches are re-armed for the same reason the server's reload re-arms them: reload
     * means "I have fixed it, try again", and a one-shot warning that has already fired would
     * otherwise stay silent about a problem that is still there.
     */
    public static void reloadOwnConfig() {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            CompanionConfig.reloadClientOwned();
            adris.altoclef.player2api.MemoryHealth.rearm();
            announce();
            say(client, Text.literal("Your own companion settings reloaded from " + CompanionConfig.configPath())
                    .formatted(Formatting.GREEN));
        } catch (Throwable e) {
            AiCompanion.LOGGER.warn("[{}] could not reload this client's config", AiCompanion.MOD_ID, e);
            say(client, Text.literal("Could not reload your config: " + e).formatted(Formatting.RED));
        }
        // Whatever the reload just found out, in the chat of the person who asked for it — the same
        // contract the server's reload has, pointed at the queue on this machine.
        for (adris.altoclef.player2api.MemoryHealth.Notice notice
                : adris.altoclef.player2api.MemoryHealth.drain()) {
            say(client, Text.literal("[memory] " + notice.text())
                    .formatted(notice.problem() ? Formatting.RED : Formatting.GREEN));
        }
    }

    private static void say(MinecraftClient client, Text text) {
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(text, false);
            }
        });
    }

    /**
     * What this server enforces, or null when it never said — an older server, or singleplayer,
     * where the local file is the authority and the screen can edit it directly.
     */
    public static JsonObject serverPolicy() {
        return serverPolicy;
    }

    /**
     * Tell the server who this player's companions are, from the file as it stands right now.
     *
     * <p>Called on join, and again whenever the config screen saves. The second one is not a nicety:
     * the roster is announced, not read, so a companion added while connected did not exist as far
     * as the server was concerned and {@code /companion spawn} answered "No companion called 'x' in
     * your config" until the player reconnected. Observed 2026-08-21 — a companion added at 17:23
     * could not be spawned until a reconnect at 17:27, through two spawn/despawn cycles and a quit
     * that all looked like the config had failed to save.
     *
     * <p>Safe to call when not connected, or against a server with no receiver: {@code canSend} is
     * false and this does nothing.
     */
    public static void announce() {
        try {
            // Read from disk rather than from the loaded statics: the announcement is about the
            // player's identities, and the roster statics on a connected client hold whatever was
            // loaded at startup, which may since have been edited and saved by the config screen.
            // Nothing to announce to, or nothing that would listen. Not an error: singleplayer and
            // a vanilla server both land here, and both are ordinary.
            if (!ClientPlayNetworking.canSend(AiCompanion.CLIENT_PROFILE)) {
                return;
            }
            JsonObject local = JsonParser
                    .parseString(Files.readString(CompanionConfig.configPath()))
                    .getAsJsonObject();
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeByteArray(ClientProfiles.buildAnnouncement(local).toString()
                    .getBytes(StandardCharsets.UTF_8));
            ClientPlayNetworking.send(AiCompanion.CLIENT_PROFILE, buf);
        } catch (Throwable e) {
            // Never fatal: without this the player simply gets the server's own roster, which is
            // exactly what happened before any of this existed.
            AiCompanion.LOGGER.warn("[{}] could not announce this client's companions ({})",
                    AiCompanion.MOD_ID, e.toString());
        }
    }
}
