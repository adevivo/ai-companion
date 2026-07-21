package com.neovetta.aicompanion.client;

import com.neovetta.aicompanion.AiCompanion;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Client entrypoint: register the companion's renderer and the config-screen opener. */
public class AiCompanionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(AiCompanion.COMPANION, CompanionRenderer::new);
        // /companion config → server sends this packet → open the Cloth Config screen. Must hop to
        // the client thread: network handlers run on netty threads, and screens are main-thread only.
        ClientPlayNetworking.registerGlobalReceiver(AiCompanion.OPEN_CONFIG_SCREEN,
                (client, handler, buf, responseSender) ->
                        client.execute(() -> client.setScreen(CompanionConfigScreen.create(client.currentScreen))));
    }
}
