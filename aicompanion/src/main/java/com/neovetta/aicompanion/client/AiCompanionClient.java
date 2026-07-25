package com.neovetta.aicompanion.client;

import com.neovetta.aicompanion.AiCompanion;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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
        // /companion config → server sends this packet → open the Cloth Config screen. Must hop to
        // the client thread: network handlers run on netty threads, and screens are main-thread only.
        ClientPlayNetworking.registerGlobalReceiver(AiCompanion.OPEN_CONFIG_SCREEN,
                (client, handler, buf, responseSender) ->
                        client.execute(() -> client.setScreen(CompanionConfigScreen.create(client.currentScreen))));

        // Radar position/health snapshot. Read the buf synchronously (it's freed after the handler
        // returns); update() only stores primitives, so no client-thread hop is needed.
        ClientPlayNetworking.registerGlobalReceiver(AiCompanion.RADAR_UPDATE,
                (client, handler, buf, responseSender) -> {
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    Identifier world = buf.readIdentifier();
                    float health = buf.readFloat();
                    float maxHealth = buf.readFloat();
                    CompanionRadarHud.update(x, y, z, world, health, maxHealth);
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

        HudRenderCallback.EVENT.register(CompanionRadarHud::render);
        HudRenderCallback.EVENT.register(CompanionTokenHud::render);

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

    /** Advance the radar mode and print the new value to the local chat. */
    private static void cycleRadarAndEcho() {
        CompanionRadarHud.Mode next = CompanionRadarHud.cycleMode();
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("Companion radar: " + next), false);
        }
    }
}
