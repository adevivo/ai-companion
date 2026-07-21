package com.neovetta.aicompanion;

import adris.altoclef.player2api.manager.ConversationManager;
import com.neovetta.aicompanion.entity.CompanionEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint for the AI Companion consumer mod. Depends on our forked PlayerEngine
 * ({@code ../engine}) for Automatone navigation + the AltoClef task engine.
 */
public class AiCompanion implements ModInitializer {
    public static final String MOD_ID = "aicompanion";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    /** Our companion entity type — a player-sized LivingEntity, tracked like a nearby player. */
    public static final EntityType<CompanionEntity> COMPANION = FabricEntityTypeBuilder
            .<CompanionEntity>createLiving()
            .spawnGroup(SpawnGroup.MISC)
            .entityFactory(CompanionEntity::new)
            .defaultAttributes(ZombieEntity::createAttributes)
            .dimensions(EntityDimensions.changing(EntityType.PLAYER.getWidth(), EntityType.PLAYER.getHeight()))
            .trackRangeBlocks(64)
            .trackedUpdateRate(1)
            .forceTrackedVelocityUpdates(true)
            .build();

    @Override
    public void onInitialize() {
        // Load sysadmin config first so LlmConfig (endpoint/model/sampling) + persona are set before spawn.
        CompanionConfig.load();
        Registry.register(Registries.ENTITY_TYPE, id("companion"), COMPANION);
        FabricDefaultAttributeRegistry.register(COMPANION, ZombieEntity.createAttributes());
        CompanionCommands.register();
        // Register the chat hook so nearby players' messages route to a companion's brain.
        ConversationManager.init();
        LOGGER.info("[{}] initialized — entity {}, /companion command, chat hook", MOD_ID, id("companion"));
    }
}
