package com.neovetta.aicompanion;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.AgentConversationData;
import adris.altoclef.player2api.Event;
import adris.altoclef.player2api.manager.ConversationManager;
import adris.altoclef.player2api.status.StatusUtils;
import adris.altoclef.tasks.movement.GetToBlockTask;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.neovetta.aicompanion.entity.CompanionEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
                        .then(CommandManager.literal("spawn")
                                .executes(ctx -> spawn(ctx.getSource(), null))
                                .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                        .suggests(ROSTER_SUGGESTIONS)
                                        .executes(ctx -> spawn(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(CommandManager.literal("goto")
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(ctx -> goTo(ctx.getSource(),
                                                BlockPosArgumentType.getBlockPos(ctx, "pos"), null))
                                        .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                                .suggests(LIVE_SUGGESTIONS)
                                                .executes(ctx -> goTo(ctx.getSource(),
                                                        BlockPosArgumentType.getBlockPos(ctx, "pos"),
                                                        StringArgumentType.getString(ctx, "name"))))))
                        .then(withOptionalName("come", CompanionCommands::come))
                        .then(withOptionalName("where", CompanionCommands::where))
                        .then(withOptionalName("stats", CompanionCommands::stats))
                        .then(withOptionalName("despawn", CompanionCommands::despawn))
                        .then(CommandManager.literal("remember")
                                .then(CommandManager.argument("fact", StringArgumentType.greedyString())
                                        .executes(ctx -> remember(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "fact"), false))))
                        .then(CommandManager.literal("rememberhere")
                                .then(CommandManager.argument("fact", StringArgumentType.greedyString())
                                        .executes(ctx -> remember(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "fact"), true))))
                        .then(CommandManager.literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(CommandManager.literal("reload").executes(ctx -> reload(ctx.getSource())))
                        .then(CommandManager.literal("config").executes(ctx -> config(ctx.getSource())))
                        .then(CommandManager.literal("radar").executes(ctx -> radar(ctx.getSource())))
                        .then(CommandManager.literal("hud").executes(ctx -> hud(ctx.getSource())))
                        .then(CommandManager.literal("tokens").executes(ctx -> tokens(ctx.getSource())))
                        .then(CommandManager.literal("skills").executes(ctx -> skills(ctx.getSource()))
                            .then(CommandManager.literal("reset")
                                    .executes(ctx -> skillsReset(ctx.getSource(), null))
                                    .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                            .suggests(BUNDLED_SUGGESTIONS)
                                            .executes(ctx -> skillsReset(ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "name"))))))
                        .then(CommandManager.literal("skill")
                                .then(CommandManager.argument("first", StringArgumentType.word())
                                        .suggests(SKILL_OR_COMPANION_SUGGESTIONS)
                                        .executes(ctx -> skill(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "first"), null))
                                        .then(CommandManager.argument("rest", StringArgumentType.greedyString())
                                                .suggests(SKILL_SUGGESTIONS)
                                                .executes(ctx -> skill(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "first"),
                                                        StringArgumentType.getString(ctx, "rest"))))))));
    }

    /**
     * A subcommand that acts on one companion, with an optional trailing name to say which.
     *
     * <p>Every one of these used to act on whatever {@link #findCompanion} happened to return first.
     * With two companions out that is a coin flip, and the feedback did not even say which one it
     * picked — so {@code /companion stats} could report the inventory of the one across the valley.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> withOptionalName(
            String literal, java.util.function.BiFunction<ServerCommandSource, String, Integer> action) {
        return CommandManager.literal(literal)
                .executes(ctx -> action.apply(ctx.getSource(), null))
                .then(CommandManager.argument("name", StringArgumentType.greedyString())
                        .suggests(LIVE_SUGGESTIONS)
                        .executes(ctx -> action.apply(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"))));
    }

    /** Every companion loaded in the caller's world, nearest first. */
    private static List<CompanionEntity> liveCompanions(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        Vec3d origin = source.getPosition();
        List<CompanionEntity> companions = new ArrayList<>(world.getEntitiesByClass(CompanionEntity.class,
                Box.of(origin, 20000, 20000, 20000), e -> true));
        companions.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(origin)));
        return companions;
    }

    /**
     * Find a companion by name anywhere on the server, in any world.
     *
     * <p>Only used by {@code spawn}'s duplicate check. Everything else is deliberately scoped to the
     * caller's world — you cannot send a command to a companion in the Nether from the Overworld, and
     * pretending otherwise would just move the failure somewhere less obvious.
     */
    private static CompanionEntity findAnywhere(MinecraftServer server, String name) {
        if (server == null || name == null) {
            return null;
        }
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof CompanionEntity companion
                        && companion.displayName().equalsIgnoreCase(name)) {
                    return companion;
                }
            }
        }
        return null;
    }

    /**
     * Find the companion a command should act on.
     *
     * <p>With a {@code name}, that companion and no other — this is the whole point of naming them.
     * Without one, the caller's own nearest, else the nearest of anyone's.
     *
     * <p>The sort is what makes the nameless case deterministic. It used to return the first
     * owner-matching entity in {@code getEntitiesByClass} order, which is arbitrary, so with two
     * companions owned by the same player every command was a coin flip between them.
     */
    private static CompanionEntity findCompanion(ServerCommandSource source, String name) {
        List<CompanionEntity> companions = liveCompanions(source);
        if (companions.isEmpty()) {
            return null;
        }
        if (name != null && !name.isBlank()) {
            String wanted = name.strip();
            return companions.stream()
                    .filter(c -> c.displayName().equalsIgnoreCase(wanted))
                    .findFirst()
                    .orElse(null);
        }
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            for (CompanionEntity c : companions) {
                if (player.getUuid().equals(c.getOwnerUuid())) {
                    return c;
                }
            }
        }
        return companions.get(0);
    }

    /**
     * Report that no companion matched, naming what is actually out there.
     *
     * <p>"No companion found nearby" was the same message whether none existed, one was in an
     * unloaded chunk, or the name was simply misspelt — three different problems with three different
     * fixes. Listing the live ones distinguishes them at a glance.
     */
    private static int noCompanion(ServerCommandSource source, String name) {
        List<CompanionEntity> companions = liveCompanions(source);
        if (companions.isEmpty()) {
            source.sendError(Text.literal(
                    "No companion found (none spawned, or it drifted into an unloaded area)."));
            return 0;
        }
        String live = companions.stream().map(CompanionEntity::displayName)
                .collect(java.util.stream.Collectors.joining(", "));
        source.sendError(Text.literal("No companion called '" + name.strip() + "'. Out right now: " + live));
        return 0;
    }

    /** Tab-completion over the identities in the config roster — what {@code spawn} accepts. */
    private static final SuggestionProvider<ServerCommandSource> ROSTER_SUGGESTIONS = (ctx, builder) -> {
        for (CompanionConfig.RosterEntry entry : CompanionConfig.roster()) {
            builder.suggest(entry.name());
        }
        return builder.buildFuture();
    };

    /** Tab-completion over the companions actually in the world — what the targeting commands accept. */
    private static final SuggestionProvider<ServerCommandSource> LIVE_SUGGESTIONS = (ctx, builder) -> {
        for (CompanionEntity companion : liveCompanions(ctx.getSource())) {
            builder.suggest(companion.displayName());
        }
        return builder.buildFuture();
    };

    /** Recall the companion to the caller — interrupts whatever it was doing and paths back. */
    private static int come(ServerCommandSource source, String name) {
        CompanionEntity companion = findCompanion(source, name);
        if (companion == null) {
            return noCompanion(source, name);
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
        String who = companion.displayName();
        source.sendFeedback(() -> Text.literal(who + " coming to " + target.toShortString()), false);
        return 1;
    }

    /**
     * List every companion in the world: who they are, how far, how healthy, what they are doing.
     *
     * <p>The missing piece when more than one is out. On 2026-07-29 an owner died, respawned 154
     * blocks away, got no reply and assumed their companion had died — then spawned a second one
     * beside the first. Nothing in the game would have told them otherwise.
     */
    /**
     * Teaches the companion a fact, and writes it to disk.
     *
     * <p>Two forms because scope cannot be guessed and getting it wrong is invisible:
     * {@code /companion remember} stores something true of the player everywhere, and
     * {@code /companion rememberhere} stores something true only in this world. "I prefer
     * cobblestone" is the first; "my base is in the taiga" is the second, and storing the second as
     * the first would have the companion assert it in every save.
     *
     * <p>Runs off the server thread: embedding is a network call and persisting writes files.
     */
    private static int remember(ServerCommandSource source, String fact, boolean thisWorldOnly) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            source.sendError(Text.literal("Only a player can teach a companion something."));
            return 0;
        }
        if (fact == null || fact.isBlank()) {
            source.sendError(Text.literal("Give it something to remember."));
            return 0;
        }

        final UUID owner = player.getUuid();
        final String worldId = thisWorldOnly
                ? adris.altoclef.player2api.WorldIdentity.idOf(source.getWorld())
                : null;
        final MinecraftServer server = source.getServer();

        // Where the player is standing, for a world memory. "rememberhere" means here, and a
        // memory about a place that carries no place is what makes the companion borrow a
        // coordinate from elsewhere in the prompt and present it as something it recalled.
        // Captured on the server thread, before the async write.
        final com.neovetta.aicompanion.memory.Place place = thisWorldOnly
                ? new com.neovetta.aicompanion.memory.Place(
                        source.getWorld().getRegistryKey().getValue().toString(),
                        player.getBlockPos().getX(),
                        player.getBlockPos().getY(),
                        player.getBlockPos().getZ())
                : null;

        CompletableFuture.runAsync(() -> {
            try {
                adris.altoclef.player2api.CompanionMemory.remember(owner, fact.strip(),
                        thisWorldOnly
                                ? com.neovetta.aicompanion.memory.MemoryScope.WORLD
                                : com.neovetta.aicompanion.memory.MemoryScope.PERSON,
                        worldId, place);
                int held = adris.altoclef.player2api.CompanionMemory.countFor(owner);
                // Back to the server thread to talk: sendFeedback is not safe off it.
                server.execute(() -> source.sendFeedback(() -> Text.literal(
                        (thisWorldOnly
                                ? "Remembered, here in this world: "
                                : "Remembered: ")
                                + fact.strip()
                                + (place == null ? "" : "  @ " + place.x() + ", " + place.y()
                                        + ", " + place.z()))
                        .formatted(Formatting.GREEN)
                        .append(Text.literal("  (" + held + " stored)")
                                .formatted(Formatting.DARK_GRAY)), false));
            } catch (Throwable e) {
                String why = e.getMessage() == null ? e.toString() : e.getMessage();
                server.execute(() -> source.sendError(Text.literal("Could not remember that: " + why)));
                AiCompanion.LOGGER.warn("[{}] /companion remember failed", AiCompanion.MOD_ID, e);
            }
        });
        return 1;
    }

    private static int list(ServerCommandSource source) {
        List<CompanionEntity> companions = liveCompanions(source);
        if (companions.isEmpty()) {
            source.sendFeedback(() -> Text.literal(
                    "No companions are out. /companion spawn to call one.").formatted(Formatting.GRAY), false);
            return 1;
        }
        ServerPlayerEntity player = source.getPlayer();
        source.sendFeedback(() -> Text.literal("Companions (" + companions.size() + "):")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);
        Vec3d origin = source.getPosition();
        for (CompanionEntity companion : companions) {
            double dist = Math.sqrt(companion.squaredDistanceTo(origin));
            boolean mine = player != null && player.getUuid().equals(companion.getOwnerUuid());
            AltoClefController ctrl = companion.getController();
            String task = ctrl == null ? "no brain attached" : describeTask(ctrl);
            String line = String.format("  %s — %.0f blocks, %.0f/%.0f hp, %s%s",
                    companion.displayName(), dist, companion.getHealth(), companion.getMaxHealth(),
                    task, mine ? "" : " (not yours)");
            source.sendFeedback(() -> Text.literal(line).formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    /**
     * One-line summary of what a companion is currently working on — the same string the model is
     * shown as {@code taskStatus}, so what you read here is what it thinks it is doing.
     */
    private static String describeTask(AltoClefController ctrl) {
        try {
            String status = StatusUtils.getTaskStatusString(ctrl);
            if (status == null || status.isBlank()) {
                return "idle";
            }
            // The engine renders an idle chain as "<Idle> " and no task at all as a sentence.
            String trimmed = status.strip();
            return trimmed.startsWith("<Idle>") || trimmed.startsWith("No tasks") ? "idle" : trimmed;
        } catch (Exception e) {
            // Never let a status readout be the thing that breaks a command.
            return "status unavailable";
        }
    }

    /**
     * Remove the companion from the world. Without this a companion that gets stuck (wedged in
     * terrain, or pathing somewhere unreachable) can only be cleared with {@code /kill}, which needs
     * cheats and takes the wrong entity as easily as the right one.
     */
    private static int despawn(ServerCommandSource source, String name) {
        CompanionEntity companion = findCompanion(source, name);
        if (companion == null) {
            return noCompanion(source, name);
        }
        // Drop its brain state too — ConversationManager keys on the entity UUID and never cleans up
        // on its own, so a spawn/despawn cycle would otherwise leak conversation data.
        ConversationManager.forget(companion.getUuid());
        String who = companion.displayName();
        companion.discard();
        source.sendFeedback(() -> Text.literal(who + " despawned."), false);
        AiCompanion.LOGGER.info("[{}] despawned companion {} (id {})", AiCompanion.MOD_ID, who,
                companion.getId());
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
        final int skillCount = CompanionSkills.all().size();
        source.sendFeedback(() -> Text.literal(String.format(
                "Config reloaded. LLM/TTS/behavior settings apply from the next reply; persona re-applied to %d live companion(s); %d skill(s) loaded.",
                count, skillCount)), false);
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

    /**
     * Cycle the caller's radar HUD mode (AUTO → ON → OFF). Like {@link #config}, the actual mode lives
     * client-side; this just sends the empty toggle packet and the client cycles + echoes the new mode
     * in chat. A client keybind cycles the same state.
     */
    private static int radar(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("/companion radar must be run by a player (it toggles a HUD)."));
            return 0;
        }
        ServerPlayNetworking.send(player, AiCompanion.RADAR_TOGGLE, PacketByteBufs.empty());
        return 1;
    }

    /**
     * Cycle the caller's companion status panel: AUTO → ON → OFF.
     *
     * <p>Same shape as {@link #radar} — the panel and its mode are client state, so this only sends the
     * empty toggle packet and the client echoes the new value. AUTO is the default and keeps the panel
     * hidden while every companion is healthy and fed; OFF is the way to be rid of it entirely.
     */
    private static int hud(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("/companion hud must be run by a player (it toggles a HUD)."));
            return 0;
        }
        ServerPlayNetworking.send(player, AiCompanion.STATUS_HUD_TOGGLE, PacketByteBufs.empty());
        return 1;
    }

    /**
     * Toggle the caller's token usage HUD. Same shape as {@link #radar}: the panel and its on/off
     * flag are client state, so this only sends the empty toggle packet and the client echoes the
     * new state. Turning it off stops the drawing, not the {@link AiCompanion#TOKEN_USAGE} packets —
     * they are ~30 bytes a second and keeping them flowing means the burn graph is still accurate
     * when the panel comes back on.
     */
    private static int tokens(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("/companion tokens must be run by a player (it toggles a HUD)."));
            return 0;
        }
        ServerPlayNetworking.send(player, AiCompanion.TOKEN_HUD_TOGGLE, PacketByteBufs.empty());
        return 1;
    }

    /** Tab-completion for {@code /companion skill <name>}: the loaded skill keys. */
    private static final SuggestionProvider<ServerCommandSource> SKILL_SUGGESTIONS = (ctx, builder) -> {
        for (String key : CompanionSkills.keys()) {
            builder.suggest(key);
        }
        return builder.buildFuture();
    };

    /**
     * Tab-completion for the first word of {@code /companion skill …}, which may be either a skill or
     * the companion to send it to. Companions first, since they are the shorter list and the reason
     * you would be typing a name at all.
     */
    private static final SuggestionProvider<ServerCommandSource> SKILL_OR_COMPANION_SUGGESTIONS =
            (ctx, builder) -> {
                for (CompanionEntity companion : liveCompanions(ctx.getSource())) {
                    builder.suggest(companion.displayName());
                }
                for (String key : CompanionSkills.keys()) {
                    builder.suggest(key);
                }
                return builder.buildFuture();
            };

    /** Tab-completion for {@code /companion skills reset [name]}: only the jar-bundled skills. */
    private static final SuggestionProvider<ServerCommandSource> BUNDLED_SUGGESTIONS = (ctx, builder) -> {
        for (String key : CompanionSkills.bundledKeys()) {
            builder.suggest(key);
        }
        return builder.buildFuture();
    };

    /**
     * Restore bundled example skills from the jar, overwriting local edits (saved to {@code .bak}).
     * Needed because the unpack-on-first-run path never overwrites, so a mod update otherwise leaves
     * everyone silently running the old skill text.
     */
    private static int skillsReset(ServerCommandSource source, String rawName) {
        String key = rawName == null ? null : CompanionSkills.key(rawName);
        if (key != null && !CompanionSkills.bundledKeys().contains(key)) {
            source.sendError(Text.literal("'" + rawName.strip() + "' is not a bundled skill. Resettable: "
                    + String.join(", ", CompanionSkills.bundledKeys())));
            return 0;
        }
        var results = CompanionSkills.resetBundled(key);
        if (results.isEmpty()) {
            source.sendError(Text.literal("Nothing to reset."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Skill reset:").formatted(Formatting.GOLD, Formatting.BOLD), false);
        for (CompanionSkills.ResetResult r : results) {
            source.sendFeedback(() -> Text.literal("  " + r.fileName() + " — " + r.detail())
                    .formatted(r.restored() ? Formatting.GRAY : Formatting.RED), false);
        }
        // Names/descriptions are advertised in the persona, so refresh live companions too.
        CompanionConfig.reloadAndApply(source.getServer());
        return 1;
    }

    /** List the loaded skills, the directory to edit, and the reload hint. */
    private static int skills(ServerCommandSource source) {
        var loaded = CompanionSkills.all();
        if (loaded.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No skills loaded. Drop .md files into "
                    + CompanionSkills.skillsDir() + " and run /companion reload.")
                    .formatted(Formatting.GRAY), false);
            return 1;
        }
        source.sendFeedback(() -> Text.literal("Loaded skills:").formatted(Formatting.GOLD, Formatting.BOLD), false);
        for (CompanionSkills.Skill s : loaded) {
            source.sendFeedback(() -> Text.literal("  " + s.key()
                    + (s.description().isEmpty() ? "" : " — " + s.description())).formatted(Formatting.GRAY), false);
        }
        source.sendFeedback(() -> Text.literal("Files: " + CompanionSkills.skillsDir()
                + " — edit, then /companion reload to update.").formatted(Formatting.GRAY), false);
        return 1;
    }

    /**
     * Teach the caller's companion a skill: inject its markdown body as a user turn into that specific
     * companion's brain queue. Targets exactly the caller's companion (unlike nearby-chat fan-out) and
     * reuses the same queue/lock the chat path uses, so there are no threading concerns. The companion's
     * spoken reply is the real acknowledgement.
     */
    private static int skill(ServerCommandSource source, String first, String rest) {
        // "/companion skill <skill>" and "/companion skill <companion> <skill>" are the same shape to
        // Brigadier, because skill names have spaces in them ("Home Guard"). Resolve by looking: if
        // the first word names a companion that is actually out, it is the target; otherwise it is
        // the start of the skill name. A companion sharing a skill's first word wins, which is a fair
        // trade for not needing a second command or a selector syntax.
        String companionName = null;
        String rawName = first;
        if (rest != null && !rest.isBlank()) {
            if (findCompanion(source, first) != null) {
                companionName = first;
                rawName = rest;
            } else {
                rawName = first + " " + rest;
            }
        }

        CompanionEntity companion = findCompanion(source, companionName);
        if (companion == null) {
            return noCompanion(source, companionName);
        }
        AltoClefController ctrl = companion.getController();
        if (ctrl == null) {
            source.sendError(Text.literal(companion.displayName()
                    + " has no active brain yet — nothing to send a skill to."));
            return 0;
        }
        CompanionSkills.Skill sk = CompanionSkills.get(CompanionSkills.key(rawName));
        if (sk == null) {
            source.sendError(Text.literal("No skill named '" + rawName.strip() + "'. Try /companion skills."));
            return 0;
        }
        AgentConversationData data = ConversationManager.getOrCreateEventQueueData(ctrl);
        data.onEvent(new Event.UserMessage(
                "Execute this skill now, step by step, using your available commands:\n\n" + sk.body(),
                source.getName()));
        String who = companion.displayName();
        source.sendFeedback(() -> Text.literal("Skill '" + sk.name() + "' sent to " + who + "."), false);
        return 1;
    }

    /** Report where the companion is and how far, so you can find one that wandered off. */
    private static int where(ServerCommandSource source, String name) {
        CompanionEntity companion = findCompanion(source, name);
        if (companion == null) {
            return noCompanion(source, name);
        }
        BlockPos pos = companion.getBlockPos();
        double dist = Math.sqrt(companion.squaredDistanceTo(source.getPosition()));
        String who = companion.displayName();
        source.sendFeedback(
                () -> Text.literal(String.format("%s at %s (%.0f blocks away)", who, pos.toShortString(), dist)),
                false);
        return 1;
    }

    /**
     * Print a readout of the companion's vitals and gear: HP, food, armor, hands, and an aggregated
     * inventory list. Food comes straight off the entity's own hunger manager (the same instance the
     * engine drives), so it's available even before a brain/controller is attached. The companion has
     * no XP — it's a {@link net.minecraft.entity.LivingEntity}, not a player — so none is shown.
     */
    private static int stats(ServerCommandSource source, String requested) {
        CompanionEntity companion = findCompanion(source, requested);
        if (companion == null) {
            return noCompanion(source, requested);
        }

        // Header: this companion's name, gold + bold.
        String name = companion.displayName();
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

    /** Send a companion walking to a block position — named, or the caller's nearest. */
    private static int goTo(ServerCommandSource source, BlockPos target, String name) {
        CompanionEntity companion = findCompanion(source, name);
        if (companion == null) {
            return noCompanion(source, name);
        }
        companion.goTo(target);
        String who = companion.displayName();
        source.sendFeedback(() -> Text.literal(who + " pathing to " + target.toShortString()), false);
        AiCompanion.LOGGER.info("[{}] goto {} for companion {} (id {})", AiCompanion.MOD_ID, target, who,
                companion.getId());
        return 1;
    }

    /**
     * Spawn a companion at the caller's feet, as one of the configured identities.
     *
     * <p>Deliberately unrestricted — spawning a second is the point. What it will not do is spawn a
     * duplicate of one already standing there: two bodies answering to one name is the exact problem
     * the roster exists to solve, and it defeats every way of telling them apart.
     */
    private static int spawn(ServerCommandSource source, String requested) {
        CompanionConfig.RosterEntry entry;
        if (requested == null || requested.isBlank()) {
            // The first configured companion that is not already in the world, so spawning twice
            // gives you two without having to name either. Always taking the FIRST entry meant a
            // second bare spawn could only ever be refused.
            entry = CompanionConfig.roster().stream()
                    .filter(e -> findAnywhere(source.getServer(), e.name()) == null)
                    .findFirst()
                    .orElse(null);
            if (entry == null) {
                int count = CompanionConfig.roster().size();
                source.sendError(Text.literal(count == 1
                        ? "Your only configured companion is already out. /companion list to find them, "
                                + "or add another under \"companions\" in /companion config."
                        : "All " + count + " configured companions are already out. /companion list "
                                + "to find them, or add another in /companion config."));
                return 0;
            }
        } else {
            entry = CompanionConfig.find(requested).orElse(null);
            if (entry == null) {
                String known = CompanionConfig.roster().stream()
                        .map(CompanionConfig.RosterEntry::name)
                        .collect(java.util.stream.Collectors.joining(", "));
                source.sendError(Text.literal("No companion called '" + requested.strip()
                        + "' in the config. Configured: " + known
                        + " — add another in /companion config, Companions tab."));
                return 0;
            }
            // Across every world, not just this one: a companion sent to the Nether is still out, and
            // spawning its double in the Overworld is exactly the duplicate this check exists to stop.
            CompanionEntity existing = findAnywhere(source.getServer(), entry.name());
            if (existing != null) {
                boolean sameWorld = existing.getWorld() == source.getWorld();
                String where = sameWorld
                        ? Math.round(Math.sqrt(existing.squaredDistanceTo(source.getPosition()))) + " blocks away"
                        : "in " + existing.getWorld().getRegistryKey().getValue().getPath();
                source.sendError(Text.literal(entry.name() + " is already out, " + where
                        + ". /companion come " + entry.name()
                        + " to call them, or /companion list to see everyone."));
                return 0;
            }
        }

        ServerWorld world = source.getWorld();
        Vec3d pos = source.getPosition();
        ServerPlayerEntity player = source.getPlayer();
        float yaw = player != null ? player.getYaw() : 0f;

        CompanionEntity companion = new CompanionEntity(AiCompanion.COMPANION, world);
        companion.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
        // Name, skin and roster binding, all from the chosen entry and all persisted in NBT.
        companion.applyRosterEntry(entry);
        world.spawnEntity(companion);

        // Attach the agent brain (owned by the spawning player). Talk to it in chat when nearby.
        // Identity comes from config/aicompanion.json (see CompanionConfig); persona/llm settings were
        // already applied to the engine statics at mod init.
        if (player != null) {
            companion.initBrain(CompanionConfig.character(entry), player);
        }

        source.sendFeedback(() -> Text.literal("Spawned " + entry.name()
                + " (id " + companion.getId() + ")"), false);
        AiCompanion.LOGGER.info("[{}] spawned companion {} at {} {} {}", AiCompanion.MOD_ID, entry.name(),
                pos.x, pos.y, pos.z);
        return 1;
    }
}
