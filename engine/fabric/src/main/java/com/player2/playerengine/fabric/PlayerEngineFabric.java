package com.player2.playerengine.fabric;

import com.player2.playerengine.PlayerEngine;
import net.fabricmc.api.ModInitializer;


public final class PlayerEngineFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        PlayerEngine.onInitialize();
    }
}
