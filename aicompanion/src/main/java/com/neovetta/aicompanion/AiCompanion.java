package com.neovetta.aicompanion;

import adris.altoclef.player2api.manager.ConversationManager;
import com.neovetta.aicompanion.entity.CompanionEntity;
import com.neovetta.aicompanion.screen.CompanionScreens;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
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

    /**
     * S2C packet (empty payload): tells the client to open the config screen. Sent by
     * {@code /companion config} — the command must live server-side because a client-registered
     * {@code /companion} root would shadow the server tree and break every other subcommand
     * (Fabric only forwards to the server when the ROOT literal is unknown to the client).
     */
    public static final Identifier OPEN_CONFIG_SCREEN = id("open_config_screen");

    /**
     * S2C packet: the companion's live position/health for the radar HUD. Sent every ~10 ticks to the
     * owner only (see {@code CompanionEntity#maybeSendRadar}). Payload: double x,y,z; Identifier world
     * id; float health, maxHealth. The client keeps the last snapshot and handles staleness itself —
     * the server holds no last-known store (the companion simply stops sending in unloaded chunks).
     */
    public static final Identifier RADAR_UPDATE = id("radar_update");

    /**
     * S2C packet (empty payload): {@code /companion radar} tells the client to cycle its radar
     * visibility mode (AUTO → ON → OFF). Server-side for the same reason as {@link #OPEN_CONFIG_SCREEN}
     * — the client cycles the static mode and echoes it in chat.
     */
    public static final Identifier RADAR_TOGGLE = id("radar_toggle");

    /**
     * S2C packet: this session's cumulative LLM token spend, for the usage HUD. Sent every ~20 ticks
     * to the owner only (see {@code CompanionEntity#maybeSendTokens}). Payload: long promptTokens,
     * completionTokens, totalTokens; varint requests. The values are cumulative rather than per-turn
     * deltas, so a dropped packet costs nothing — the client derives its per-minute burn graph by
     * diffing consecutive snapshots and simply sees a bigger jump after a gap.
     */
    public static final Identifier TOKEN_USAGE = id("token_usage");

    /**
     * S2C packet (empty payload): {@code /companion tokens} tells the client to flip the token HUD
     * on or off. Server-side for the same reason as {@link #OPEN_CONFIG_SCREEN} — the client flips
     * the static flag and echoes the new state in chat.
     */
    public static final Identifier TOKEN_HUD_TOGGLE = id("token_hud_toggle");

    /**
     * Zombie attributes plus {@code GENERIC_ATTACK_SPEED}.
     *
     * <p>The zombie set is a convenient starting point (20 health, 0.23 speed, 3 attack damage, 2 armor)
     * but it omits attack speed, which no mob has and every player does. Without it,
     * {@code CompanionEntity#getAttackCooldownProgressPerTick} has nothing to read and a held weapon's
     * attack-speed modifier has no attribute to modify — so the companion swings at a fixed cadence
     * regardless of what it is holding. 4.0 is the vanilla player base.
     */
    public static DefaultAttributeContainer.Builder createCompanionAttributes() {
        return ZombieEntity.createAttributes()
                .add(EntityAttributes.GENERIC_ATTACK_SPEED, 4.0);
    }

    /** Our companion entity type — a player-sized LivingEntity, tracked like a nearby player. */
    public static final EntityType<CompanionEntity> COMPANION = FabricEntityTypeBuilder
            .<CompanionEntity>createLiving()
            .spawnGroup(SpawnGroup.MISC)
            .entityFactory(CompanionEntity::new)
            .defaultAttributes(AiCompanion::createCompanionAttributes)
            .dimensions(EntityDimensions.changing(EntityType.PLAYER.getWidth(), EntityType.PLAYER.getHeight()))
            .trackRangeBlocks(64)
            .trackedUpdateRate(1)
            .forceTrackedVelocityUpdates(true)
            .build();

    @Override
    public void onInitialize() {
        // Load skills before config: CompanionConfig.apply() advertises the loaded skills in the
        // persona, so they must be scanned first.
        CompanionSkills.load();
        // Load sysadmin config first so LlmConfig (endpoint/model/sampling) + persona are set before spawn.
        CompanionConfig.load();
        Registry.register(Registries.ENTITY_TYPE, id("companion"), COMPANION);
        FabricDefaultAttributeRegistry.register(COMPANION, createCompanionAttributes());
        CompanionScreens.register();
        CompanionCommands.register();
        // Register the chat hook so nearby players' messages route to a companion's brain.
        ConversationManager.init();
        LOGGER.info("[{}] initialized — entity {}, /companion command, chat hook", MOD_ID, id("companion"));
    }
}
