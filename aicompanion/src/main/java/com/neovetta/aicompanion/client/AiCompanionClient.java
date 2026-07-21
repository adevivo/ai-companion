package com.neovetta.aicompanion.client;

import com.neovetta.aicompanion.AiCompanion;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Client entrypoint: register the companion's renderer so it draws in-world. */
public class AiCompanionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(AiCompanion.COMPANION, CompanionRenderer::new);
    }
}
