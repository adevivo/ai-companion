package com.neovetta.aicompanion;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

    /**
     * Server-side half of the client brain: who can think, results coming back, and cleanup.
     *
     * <p>Registered unconditionally. The switch that matters is {@code llm.clientBrain}, checked
     * when a turn is dispatched — a client announcing itself costs nothing and means the setting can
     * be turned on at runtime without anyone reconnecting.
     */
    private static void registerBrainReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(
                adris.altoclef.player2api.brain.BrainWire.HELLO,
                (server, player, handler, buf, sender) ->
                        adris.altoclef.player2api.brain.NetworkBrainTransport
                                .markCapable(player.getUuid()));

        ServerPlayNetworking.registerGlobalReceiver(
                adris.altoclef.player2api.brain.BrainWire.TURN_RESULT,
                (server, player, handler, buf, sender) -> {
                    java.util.UUID requestId = buf.readUuid();
                    String reply = new String(buf.readByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                    String error = buf.readString(512);
                    // Off the network thread and onto the server thread: delivering a result resumes
                    // a conversation turn, which touches companion state.
                    server.execute(() -> adris.altoclef.player2api.brain.NetworkBrainTransport
                            .deliver(requestId, reply, error));
                });

        // A player who quits mid-turn would otherwise leave a request nobody will ever answer, and
        // the same companion is reused on reconnect — so it would look permanently mute, not late.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {
                    java.util.UUID id = handler.getPlayer().getUuid();
                    adris.altoclef.player2api.brain.NetworkBrainTransport.forget(id);
                    // Their roster and trigger prefix leave with them. Keeping either would let a
                    // player who has gone go on shaping a server they are no longer connected to.
                    ClientProfiles.forget(id);
                    // And their companions go with them. Left standing they are unresponsive bodies
                    // drawn as default Steve to everyone else, holding a slot in the server's cap
                    // for as long as their owner stays away. Never removes one it could not first
                    // write and read back — see CompanionParking.
                    CompanionParking.park(server, id);
                });

        // Bring them back when their owner returns. Registered here rather than beside the client
        // packet handlers because it is world state, not a conversation.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) ->
                        CompanionParking.restore(server, handler.getPlayer()));
    }

    /**
     * The half of configuration that has to cross the wire, in both directions.
     *
     * <p>Most of it needs no plumbing: each side loads its own file and acts on what it read. These
     * two are the exceptions, and both for the same reason — the machine that <em>owns</em> the
     * setting is not the machine that <em>acts</em> on it. A player owns their companions but the
     * server spawns them; an operator owns the rules but the player needs to see them.
     */
    private static void registerConfigReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(CLIENT_PROFILE,
                (server, player, handler, buf, sender) -> {
                    String json = new String(buf.readByteArray(),
                            java.nio.charset.StandardCharsets.UTF_8);
                    // Onto the server thread: this reads the player list for the impersonation check
                    // and talks to the player, neither of which is safe off it.
                    server.execute(() -> {
                        try {
                            com.google.gson.JsonObject payload = com.google.gson.JsonParser
                                    .parseString(json).getAsJsonObject();
                            for (String problem : ClientProfiles.announce(player, payload)) {
                                player.sendMessage(net.minecraft.text.Text.literal("[companion] " + problem)
                                        .formatted(net.minecraft.util.Formatting.YELLOW), false);
                            }
                        } catch (Throwable e) {
                            // A malformed announcement means a broken or hostile client, not a
                            // reason to drop the player: they fall back to this server's roster and
                            // are told so, rather than finding /companion spawn mysteriously empty.
                            LOGGER.warn("[{}] unreadable roster announcement from {}", MOD_ID,
                                    player.getName().getString(), e);
                            player.sendMessage(net.minecraft.text.Text.literal(
                                    "[companion] your companion config could not be read — using this "
                                            + "server's defaults").formatted(net.minecraft.util.Formatting.YELLOW),
                                    false);
                        }
                        sendServerPolicy(player);
                    });
                });
    }

    /**
     * Tell one client what this server enforces, for the read-only Server tab.
     *
     * <p>Display only. Nothing client-side acts on these — the caps, the permission levels and the
     * chat routing all run here — but a config screen that showed the client's own copy of the block
     * would be showing values nobody reads, which is worse than showing nothing.
     */
    public static void sendServerPolicy(net.minecraft.server.network.ServerPlayerEntity player) {
        try {
            net.minecraft.network.PacketByteBuf out = PacketByteBufs.create();
            out.writeByteArray(CompanionConfig.serverPolicyJson().toString()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ServerPlayNetworking.send(player, SERVER_POLICY, out);
        } catch (Throwable e) {
            LOGGER.warn("[{}] could not send the server policy to {}", MOD_ID,
                    player.getName().getString(), e);
        }
    }

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
     * C2S: the client's own companions and trigger prefix, sent once on join.
     *
     * <p>⚠️ Untrusted. See {@link ClientProfiles} and {@code RosterGuard} — this is the first place
     * the mod acts on something a connecting client wrote, and it reaches chat and the system
     * prompt.
     */
    public static final Identifier CLIENT_PROFILE = id("client_profile");

    /** S2C: the operator's {@code server} block, for the read-only Server tab. Display only. */
    public static final Identifier SERVER_POLICY = id("server_policy");

    /**
     * S2C packet (empty payload): re-read your own config file and apply the player-owned half.
     *
     * <p>{@code /companion reload} used to be operator-only, which was right about the server's half
     * and wrong about everyone's own. A non-operator on a dedicated server could not reload the
     * settings they are the only one who reads — their endpoints, their memory switches — and the
     * config screen could not do it for them either, so the only way to apply an edit was to quit
     * the game. Reload is now available to everyone and does the half that belongs to the caller;
     * the server's half still needs permission. See {@code CompanionCommands#reload}.
     */
    public static final Identifier RELOAD_CLIENT_CONFIG = id("reload_client_config");

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
     * S2C packet (empty payload): {@code /companion hud} cycles the companion status panel
     * (AUTO → ON → OFF). Same shape as {@link #RADAR_TOGGLE} — the mode is client state, so the server
     * only nudges and the client echoes the new value.
     */
    public static final Identifier STATUS_HUD_TOGGLE = id("status_hud_toggle");

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
     * Zombie attributes, corrected to a player's combat stat line.
     *
     * <p>The zombie set is a convenient starting point for the non-combat attributes (20 health,
     * 0.23 speed, follow range) but its combat numbers are a mob's, and a companion that fights the
     * player's fights should fight on the player's terms:
     *
     * <ul>
     *   <li>{@code ATTACK_SPEED} — zombies have none, every player does. Without it,
     *       {@link CompanionEntity#getAttackCooldownProgressPerTick} has nothing to read and a held
     *       weapon's attack-speed modifier has no attribute to modify, so the companion swings at a
     *       fixed cadence regardless of what it is holding. 4.0 is the vanilla player base.
     *   <li>{@code ATTACK_DAMAGE} — the zombie default is 3.0 against a player's 1.0. That is 3x
     *       bare-handed, and it rides on top of every weapon: a diamond sword (+7) hit for 10.0
     *       instead of 8.0. Gear should be the only thing that makes a companion hit harder.
     *   <li>{@code ARMOR} — the zombie default is 2.0 against a player's 0.0, i.e. two free points of
     *       armour with nothing equipped.
     * </ul>
     *
     * <p>{@code MOVEMENT_SPEED} is deliberately left at the mob-scale 0.23. Baritone drives this
     * entity through input overrides and that value is load-bearing for navigation; if it needs
     * tuning it gets its own change and its own playtest, so a movement regression can't hide inside
     * a combat fix.
     *
     * <p>Deliberately reads no config. This builder runs during static init — before
     * {@link #onInitialize()} has loaded {@code aicompanion.json} — so anything it read would be the
     * built-in default anyway. Server overrides are applied per-entity by
     * {@link CompanionEntity#applyCombatConfig()}, which has the further advantage of taking effect
     * on {@code /companion reload} instead of only at restart.
     */
    public static DefaultAttributeContainer.Builder createCompanionAttributes() {
        return ZombieEntity.createAttributes()
                .add(EntityAttributes.GENERIC_ATTACK_SPEED, 4.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, CombatConfig.DEFAULT_ATTACK_DAMAGE)
                .add(EntityAttributes.GENERIC_ARMOR, CombatConfig.DEFAULT_ARMOR);
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
        registerBrainReceivers();
        registerConfigReceivers();
        LOGGER.info("[{}] initialized — entity {}, /companion command, chat hook", MOD_ID, id("companion"));
    }
}
