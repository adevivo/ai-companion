package com.neovetta.aicompanion;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.manager.ConversationManager;
import adris.altoclef.tasks.movement.GetToBlockTask;
import com.neovetta.aicompanion.entity.CompanionEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                        .then(CommandManager.literal("where").executes(ctx -> where(ctx.getSource())))
                        .then(CommandManager.literal("stats").executes(ctx -> stats(ctx.getSource())))
                        .then(CommandManager.literal("despawn").executes(ctx -> despawn(ctx.getSource())))
                        .then(CommandManager.literal("reload").executes(ctx -> reload(ctx.getSource())))
                        .then(CommandManager.literal("config").executes(ctx -> config(ctx.getSource())))));
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

    /**
     * Remove the companion from the world. Without this a companion that gets stuck (wedged in
     * terrain, or pathing somewhere unreachable) can only be cleared with {@code /kill}, which needs
     * cheats and takes the wrong entity as easily as the right one.
     */
    private static int despawn(ServerCommandSource source) {
        CompanionEntity companion = findCompanion(source);
        if (companion == null) {
            source.sendError(Text.literal("No companion found nearby (it may be in an unloaded area)."));
            return 0;
        }
        // Drop its brain state too — ConversationManager keys on the entity UUID and never cleans up
        // on its own, so a spawn/despawn cycle would otherwise leak conversation data.
        ConversationManager.forget(companion.getUuid());
        companion.discard();
        source.sendFeedback(() -> Text.literal("Companion despawned."), false);
        AiCompanion.LOGGER.info("[{}] despawned companion id {}", AiCompanion.MOD_ID, companion.getId());
        return 1;
    }

    /**
     * Re-read {@code config/aicompanion.json} and apply it without a restart. LLM/TTS/behavior
     * settings are volatile statics read at call time, so they take effect on the next request; the
     * persona is re-applied to every live companion's brain via
     * {@code AIPersistantData.updateSystemPrompt()}. Only name/description/skin stay baked into the
     * entity — those need a despawn/spawn cycle, which the feedback says explicitly.
     */
    private static int reload(ServerCommandSource source) {
        final int count = CompanionConfig.reloadAndApply(source.getServer());
        source.sendFeedback(() -> Text.literal(String.format(
                "Config reloaded. LLM/TTS/behavior settings apply from the next reply; persona re-applied to %d live companion(s).",
                count)), false);
        source.sendFeedback(() -> Text.literal(
                "Note: name/description/skin changes need /companion despawn + /companion spawn."), false);
        AiCompanion.LOGGER.info("[{}] config reloaded via /companion reload ({} live companion(s) updated)",
                AiCompanion.MOD_ID, count);
        return 1;
    }

    /**
     * Pop the in-game config screen on the caller's client. The command itself is server-side (see
     * {@link AiCompanion#OPEN_CONFIG_SCREEN} for why it can't be a client command), so it just sends
     * the empty S2C packet; the client receiver opens the Cloth Config screen.
     */
    private static int config(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("/companion config must be run by a player (it opens a screen)."));
            return 0;
        }
        ServerPlayNetworking.send(player, AiCompanion.OPEN_CONFIG_SCREEN, PacketByteBufs.empty());
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

    /**
     * Print a readout of the companion's vitals and gear: HP, food, armor, hands, and an aggregated
     * inventory list. Food comes straight off the entity's own hunger manager (the same instance the
     * engine drives), so it's available even before a brain/controller is attached. The companion has
     * no XP — it's a {@link net.minecraft.entity.LivingEntity}, not a player — so none is shown.
     */
    private static int stats(ServerCommandSource source) {
        CompanionEntity companion = findCompanion(source);
        if (companion == null) {
            source.sendError(Text.literal("No companion found nearby (it may be in an unloaded area)."));
            return 0;
        }

        // Header: custom name (or configured name), gold + bold.
        String name = companion.getCustomName() != null
                ? companion.getCustomName().getString() : CompanionConfig.name();
        source.sendFeedback(() -> Text.literal("— " + name + " —")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);

        // Health + food on one line.
        int food = companion.getHungerManager().getFoodLevel();
        float sat = companion.getHungerManager().getSaturationLevel();
        source.sendFeedback(() -> Text.literal(String.format("Health: %.1f/%.0f   Food: %d/20 (sat %.1f)",
                companion.getHealth(), companion.getMaxHealth(), food, sat)), false);

        // Armor: helmet → boots, non-empty only.
        List<String> armor = new ArrayList<>();
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            String d = describe(companion.getEquippedStack(slot));
            if (d != null) {
                armor.add(d);
            }
        }
        source.sendFeedback(() -> Text.literal(
                "Armor: " + (armor.isEmpty() ? "none" : String.join(", ", armor))), false);

        // Hands.
        String main = describe(companion.getEquippedStack(EquipmentSlot.MAINHAND));
        String off = describe(companion.getEquippedStack(EquipmentSlot.OFFHAND));
        source.sendFeedback(() -> Text.literal("Hands: main = " + (main == null ? "empty" : main)
                + ", off = " + (off == null ? "empty" : off)), false);

        // Inventory: aggregate counts per item, most first.
        Map<String, Integer> counts = new LinkedHashMap<>();
        int usedSlots = 0;
        int totalSlots = companion.inventory.main.size();
        for (ItemStack stack : companion.inventory.main) {
            if (stack.isEmpty()) {
                continue;
            }
            usedSlots++;
            String path = Registries.ITEM.getId(stack.getItem()).getPath();
            counts.merge(path, stack.getCount(), Integer::sum);
        }
        final int used = usedSlots;
        source.sendFeedback(() -> Text.literal(
                String.format("Inventory (%d/%d slots):", used, totalSlots)), false);
        if (!counts.isEmpty()) {
            String list = counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .map(e -> e.getValue() + "× " + e.getKey())
                    .reduce((a, b) -> a + ", " + b).orElse("");
            source.sendFeedback(() -> Text.literal("  " + list).formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    /**
     * Human-readable one-item summary: {@code item_path} plus {@code (remaining/max)} durability for
     * damageable items. Returns null for an empty stack so callers can render "empty"/"none".
     */
    private static String describe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        if (stack.isDamageable()) {
            return path + " (" + (stack.getMaxDamage() - stack.getDamage()) + "/" + stack.getMaxDamage() + ")";
        }
        return path;
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
