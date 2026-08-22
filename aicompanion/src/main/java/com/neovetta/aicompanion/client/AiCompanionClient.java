package com.neovetta.aicompanion.client;

import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.screen.CompanionScreens;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Client entrypoint: register the companion's renderer, the config-screen opener, and the radar HUD. */
public class AiCompanionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(AiCompanion.COMPANION, CompanionRenderer::new);
        // Right-click a companion with an empty hand to open its inventory (see CompanionEntity#interact).
        MenuScreens.register(CompanionScreens.TYPE, CompanionScreen::new);
        // /companion config → server sends this packet → open the Cloth Config screen. Must hop to
        // the client thread: network handlers run on netty threads, and screens are main-thread only.
        // Thinking for our own companion when the server asks. Registers a JOIN handshake plus one
        // receiver; does nothing at all unless the server has llm.clientBrain on and asks.
        ClientBrain.register();
        // Announce our own companions and trigger prefix at join, and take the operator's rules back
        // for the read-only Server tab. Both directions no-op against a server too old to have the
        // channels registered, which is the ordinary case and not an error.
        ClientConfigSync.register();

        // /companion reload → re-read OUR file and apply the half that is ours. Off the netty
        // thread: this reads a file from disk and rebuilds the roster, and a network read thread is
        // the wrong place for either — observed running on "Netty Client IO #1" before this hop.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AiCompanion.RELOAD_CLIENT_CONFIG,
                (buf, context) ->
                        java.util.concurrent.CompletableFuture.runAsync(ClientConfigSync::reloadOwnConfig));

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AiCompanion.OPEN_CONFIG_SCREEN,
                (buf, context) -> context.queue(() -> {
                    Minecraft client = Minecraft.getInstance();
                    client.setScreen(CompanionConfigScreen.create(client.screen));
                }));

        // Radar position/health snapshot. Read the buf synchronously (it's freed after the handler
        // returns); update() only stores primitives, so no client-thread hop is needed.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AiCompanion.RADAR_UPDATE,
                (buf, context) -> {
                    int entityId = buf.readVarInt();
                    String name = buf.readUtf();
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    Identifier world = buf.readIdentifier();
                    float health = buf.readFloat();
                    float maxHealth = buf.readFloat();
                    int food = buf.readVarInt();
                    float saturation = buf.readFloat();
                    CompanionRadarHud.update(entityId, name, x, y, z, world, health, maxHealth);
                    CompanionStatusHud.update(entityId, name, world, health, maxHealth, food, saturation);
                });

        // /companion radar → cycle the HUD mode and echo it. Hop to the client thread to touch the player.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AiCompanion.RADAR_TOGGLE,
                (buf, context) -> context.queue(AiCompanionClient::cycleRadarAndEcho));

        // Cumulative session token spend for the usage HUD. Same no-hop reasoning as the radar.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AiCompanion.TOKEN_USAGE,
                (buf, context) -> {
                    long promptTokens = buf.readLong();
                    long completionTokens = buf.readLong();
                    long totalTokens = buf.readLong();
                    int requests = buf.readVarInt();
                    CompanionTokenHud.update(promptTokens, completionTokens, totalTokens, requests);
                });

        // /companion hud → cycle the status panel and echo it. Client thread, same as the radar toggle.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AiCompanion.STATUS_HUD_TOGGLE,
                (buf, context) -> context.queue(AiCompanionClient::cycleStatusHudAndEcho));

        // /companion tokens → flip the usage panel and echo it. Client thread, same as the radar toggle.
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AiCompanion.TOKEN_HUD_TOGGLE,
                (buf, context) -> context.queue(AiCompanionClient::toggleTokenHudAndEcho));

        // Count our own spend when we are the ones spending it — see CompanionTokenHud#selfUpdate.
        // Registered unconditionally: it is a counter read and a modulo, and it returns immediately
        // on any client that has made no LLM calls of its own, which is every client on a
        // server-side brain.
        ClientTickEvents.END_CLIENT_TICK.register(client -> CompanionTokenHud.selfUpdate());

        HudRenderCallback.EVENT.register(CompanionRadarHud::render);
        HudRenderCallback.EVENT.register(CompanionStatusHud::render);
        HudRenderCallback.EVENT.register(CompanionTokenHud::render);

        // Radar snapshots are static and keyed by entity id, so they have to go when the world does —
        // otherwise the next world's HUD briefly shows the last one's companions.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CompanionRadarHud.clear();
            CompanionStatusHud.clear();
        });

        // Client keybind that cycles the same mode. Default unbound to avoid conflicts — the user can
        // assign it in Controls, or just use /companion radar.
        KeyMapping radarKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.aicompanion.radar", InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getKeyCode(), "key.category.aicompanion"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (radarKey.consumeClick()) {
                cycleRadarAndEcho();
            }
        });
    }

    /** Flip the token usage panel and print the new state to the local chat. */
    private static void toggleTokenHudAndEcho() {
        boolean on = CompanionTokenHud.toggle();
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("Companion token HUD: " + (on ? "ON" : "OFF")), false);
        }
    }

    /** Advance the status panel mode and print the new value to the local chat. */
    private static void cycleStatusHudAndEcho() {
        CompanionStatusHud.Mode next = CompanionStatusHud.cycleMode();
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.player != null) {
            String hint = switch (next) {
                case AUTO -> " (shown only when one is hurt or hungry)";
                case ON -> " (always shown)";
                case OFF -> " (hidden)";
            };
            client.player.displayClientMessage(Component.literal("Companion status HUD: " + next + hint), false);
        }
    }

    /** Advance the radar mode and print the new value to the local chat. */
    private static void cycleRadarAndEcho() {
        CompanionRadarHud.Mode next = CompanionRadarHud.cycleMode();
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("Companion radar: " + next), false);
        }
    }
}
