package com.neovetta.aicompanion;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.movement.GetToBlockTask;
import com.neovetta.aicompanion.entity.CompanionEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

/**
 * Dev/admin commands for the companion. Phase 1: {@code /companion spawn} drops a companion at the
 * caller's feet so we can watch it in-world. Navigation ({@code /companion goto}) is added with the
 * AltoClefController wiring.
 */
public final class CompanionCommands {

    private CompanionCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("companion")
                        // Op-gate on real servers, but always allow the local single-player owner.
                        // Survival worlds default "Allow Cheats" OFF (perm level 0), which would
                        // otherwise hide this command entirely; creative worlds default it ON.
                        .requires(src -> src.getServer().isSingleplayer() || src.hasPermissionLevel(2))
                        .then(CommandManager.literal("spawn").executes(ctx -> spawn(ctx.getSource())))
                        .then(CommandManager.literal("goto")
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(ctx -> goTo(ctx.getSource(),
                                                BlockPosArgumentType.getBlockPos(ctx, "pos")))))
                        .then(CommandManager.literal("come").executes(ctx -> come(ctx.getSource())))
                        .then(CommandManager.literal("where").executes(ctx -> where(ctx.getSource())))));
    }

    /**
     * Find the caller's companion regardless of distance (it may have wandered off). Searches a large
     * region of loaded entities, preferring one owned by the caller, else the nearest. Returns null if
     * none are loaded (e.g. it drifted into an unloaded chunk).
     */
    private static CompanionEntity findCompanion(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        Vec3d origin = source.getPosition();
        List<CompanionEntity> companions = world.getEntitiesByClass(CompanionEntity.class,
                Box.of(origin, 20000, 20000, 20000), e -> true);
        if (companions.isEmpty()) {
            return null;
        }
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            for (CompanionEntity c : companions) {
                AltoClefController ctrl = c.getController();
                if (ctrl != null && ctrl.getOwner() != null
                        && ctrl.getOwner().getUuid().equals(player.getUuid())) {
                    return c;
                }
            }
        }
        return companions.stream()
                .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(origin)))
                .orElse(null);
    }

    /** Recall the companion to the caller — interrupts whatever it was doing and paths back. */
    private static int come(ServerCommandSource source) {
        CompanionEntity companion = findCompanion(source);
        if (companion == null) {
            source.sendError(Text.literal("No companion found nearby (it may be in an unloaded area)."));
            return 0;
        }
        ServerPlayerEntity player = source.getPlayer();
        BlockPos target = player != null ? player.getBlockPos() : companion.getBlockPos();
        AltoClefController ctrl = companion.getController();
        if (ctrl != null) {
            // Controller-aware: replaces the current task so it stops "running off" and comes back.
            ctrl.runUserTask(new GetToBlockTask(target));
        } else {
            companion.goTo(target);
        }
        source.sendFeedback(() -> Text.literal("Companion coming to " + target.toShortString()), false);
        return 1;
    }

    /** Report where the companion is and how far, so you can find one that wandered off. */
    private static int where(ServerCommandSource source) {
        CompanionEntity companion = findCompanion(source);
        if (companion == null) {
            source.sendError(Text.literal("No companion found nearby (it may be in an unloaded area)."));
            return 0;
        }
        BlockPos pos = companion.getBlockPos();
        double dist = Math.sqrt(companion.squaredDistanceTo(source.getPosition()));
        source.sendFeedback(
                () -> Text.literal(String.format("Companion at %s (%.0f blocks away)", pos.toShortString(), dist)),
                false);
        return 1;
    }

    /** Send the nearest companion (within 256 blocks of the caller) walking to a block position. */
    private static int goTo(ServerCommandSource source, BlockPos target) {
        ServerWorld world = source.getWorld();
        Vec3d origin = source.getPosition();
        List<CompanionEntity> companions = world.getEntitiesByClass(CompanionEntity.class,
                Box.of(origin, 512, 512, 512), e -> true);
        if (companions.isEmpty()) {
            source.sendError(Text.literal("No companion nearby to send."));
            return 0;
        }
        CompanionEntity companion = companions.stream()
                .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(origin)))
                .orElseThrow();
        companion.goTo(target);
        source.sendFeedback(() -> Text.literal("Companion pathing to " + target.toShortString()), false);
        AiCompanion.LOGGER.info("[{}] goto {} for companion id {}", AiCompanion.MOD_ID, target, companion.getId());
        return 1;
    }

    private static int spawn(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        Vec3d pos = source.getPosition();
        ServerPlayerEntity player = source.getPlayer();
        float yaw = player != null ? player.getYaw() : 0f;

        CompanionEntity companion = new CompanionEntity(AiCompanion.COMPANION, world);
        companion.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
        // Show the configured name above its head (persisted in NBT), not the entity-type key.
        companion.setCustomName(Text.literal(CompanionConfig.name()));
        companion.setCustomNameVisible(true);
        world.spawnEntity(companion);

        // Attach the agent brain (owned by the spawning player). Talk to it in chat when nearby.
        // Identity comes from config/aicompanion.json (see CompanionConfig); persona/llm settings were
        // already applied to the engine statics at mod init.
        if (player != null) {
            companion.initBrain(CompanionConfig.character(), player);
        }

        source.sendFeedback(() -> Text.literal("Spawned AI companion (id " + companion.getId() + ")"), false);
        AiCompanion.LOGGER.info("[{}] spawned companion at {} {} {}", AiCompanion.MOD_ID, pos.x, pos.y, pos.z);
        return 1;
    }
}
