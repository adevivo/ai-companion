package com.player2.playerengine.forge;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.PlayerEngineClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;


@Mod(PlayerEngine.MOD_ID)
public final class PlayerEngineForge {
    public PlayerEngineForge(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
    }

    private void setup(final FMLCommonSetupEvent event) {
        PlayerEngine.onInitialize();
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        PlayerEngineClient.onInitializeClient();
    }
}
