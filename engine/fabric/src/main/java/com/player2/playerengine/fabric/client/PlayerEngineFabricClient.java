package com.player2.playerengine.fabric.client;

import com.player2.playerengine.PlayerEngineClient;
import net.fabricmc.api.ClientModInitializer;

public final class PlayerEngineFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PlayerEngineClient.onInitializeClient();
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
    }
}
