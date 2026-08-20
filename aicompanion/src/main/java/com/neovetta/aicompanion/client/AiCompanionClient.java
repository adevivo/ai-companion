package com.neovetta.aicompanion.client;

import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.screen.CompanionScreens;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import com.mojang.blaze3d.platform.InputUtil;
import net.minecraft.client.option.KeyBind;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Client entrypoint: register the companion's renderer, the config-screen opener, and the radar HUD. */
public class AiCompanionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(AiCompanion.COMPANION, CompanionRenderer::new);
        // Right-click a companion with an empty hand to open its inventory (see CompanionEntity#interact).
        HandledScreens.register(CompanionScreens.TYPE, CompanionScreen::new);
        // /companion config → server sends this packet → open the Cloth Config screen. Must hop to
        // the client thread: network handlers run on netty threads, and screens are main-thread only.
        // Thinking for our own companion when the server asks. Registers a JOIN handshake plus one
        // receiver; does nothing at all unless the server has llm.clientBrain on and asks.
        ClientBrain.register();

        ClientPlayNetworking.registerGlobalReceiver(AiCompanion.OPEN_CONFIG_SCREEN,
                (client, handler, buf, responseSender) ->
                        client.execute(() -> client.setScreen(CompanionConfigScreen.create(client.currentScreen))));

        // Radar position/health snapshot. Read the buf synchronously (it's freed after the handler
        // returns); update() only stores primitives, so no client-thread hop is needed.
        ClientPlayNetworking.registerGlobalReceiver(AiCompanion.RADAR_UPDATE,
                (client, handler, buf, responseSender) -> {
                    int entityId = buf.readVarInt();
                    String name = buf.readString();
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
        ClientPlayNetworking.registerGlobalReceiver(AiCompanion.RADAR_TOGGLE,
                (client, handler, buf, responseSender) -> client.execute(AiCompanionClient::cycleRadarAndEcho));

        // Cumulative session token spend for the usage HUD. Same no-hop reasoning as the radar.
        ClientPlayNetworking.registerGlobalReceiver(AiCompanion.TOKEN_USAGE,
                (client, handler, buf, responseSender) -> {
                    long promptTokens = buf.readLong();
                    long completionTokens = buf.readLong();
                    long totalTokens = buf.readLong();
                    int requests = buf.readVarInt();
                    CompanionTokenHud.update(promptTokens, completionTokens, totalTokens, requests);
                });

        // /companion hud → cycle the status panel and echo it. Client thread, same as the radar toggle.
        ClientPlayNetworking.registerGlobalReceiver(AiCompanion.STATUS_HUD_TOGGLE,
                (client, handler, buf, responseSender) -> client.execute(AiCompanionClient::cycleStatusHudAndEcho));

        // /companion tokens → flip the usage panel and echo it. Client thread, same as the radar toggle.
        ClientPlayNetworking.registerGlobalReceiver(AiCompanion.TOKEN_HUD_TOGGLE,
                (client, handler, buf, responseSender) -> client.execute(AiCompanionClient::toggleTokenHudAndEcho));

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
        KeyBind radarKey = KeyBindingHelper.registerKeyBinding(new KeyBind(
                "key.aicompanion.radar", InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getKeyCode(), "key.category.aicompanion"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (radarKey.wasPressed()) {
                cycleRadarAndEcho();
            }
        });
    }

    /** Flip the token usage panel and print the new state to the local chat. */
    private static void toggleTokenHudAndEcho() {
        boolean on = CompanionTokenHud.toggle();
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("Companion token HUD: " + (on ? "ON" : "OFF")), false);
        }
    }

    /** Advance the status panel mode and print the new value to the local chat. */
    private static void cycleStatusHudAndEcho() {
        CompanionStatusHud.Mode next = CompanionStatusHud.cycleMode();
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player != null) {
            String hint = switch (next) {
                case AUTO -> " (shown only when one is hurt or hungry)";
                case ON -> " (always shown)";
                case OFF -> " (hidden)";
            };
            client.player.sendMessage(Text.literal("Companion status HUD: " + next + hint), false);
        }
    }

    /** Advance the radar mode and print the new value to the local chat. */
    private static void cycleRadarAndEcho() {
        CompanionRadarHud.Mode next = CompanionRadarHud.cycleMode();
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("Companion radar: " + next), false);
        }
    }
}
