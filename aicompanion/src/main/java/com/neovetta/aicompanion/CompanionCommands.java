package com.neovetta.aicompanion;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.AgentConversationData;
import adris.altoclef.player2api.Event;
import adris.altoclef.player2api.manager.ConversationManager;
import adris.altoclef.player2api.status.StatusUtils;
import adris.altoclef.player2api.ServerPolicy;
import adris.altoclef.tasks.movement.GetToBlockTask;
import me.lucko.fabric.api.permissions.v0.Permissions;
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

    /** Permission nodes are namespaced so an operator can grant them in LuckPerms by group. */
    private static final String NODE = "aicompanion.command.";

    /** Acting on a companion that is not yours. Level 2, so console and operators keep working. */
    private static final String ADMIN_NODE = "aicompanion.admin";

    /**
     * A subcommand any player may run on their own companion.
     *
     * <p>Level 0 by default: the whole command tree used to be gated at the root on
     * {@code isSingleplayer() || hasPermissionLevel(2)}, a leftover from when this was singleplayer
     * only, which meant no ordinary player could use the mod at all.
     *
     * <p>The level is read at evaluation time rather than baked in at registration, so
     * {@code server.allowPlayerCommands} takes effect on {@code /companion reload} instead of only
     * at restart. Locking the mod down raises these to 2 rather than removing them — one predicate
     * with two levels, instead of a second gate that can disagree with the first.
     */
    private static java.util.function.Predicate<ServerCommandSource> player(String node) {
        return src -> Permissions.check(src, NODE + node, ServerPolicy.allowPlayerCommands ? 0 : 2);
    }

    /** A subcommand that changes the server for everybody: config, skill files, a global reload. */
    private static java.util.function.Predicate<ServerCommandSource> operator(String node) {
        return src -> isWorldHost(src) || Permissions.check(src, NODE + node, 2);
    }

    /** Whether this caller may act on companions that are not theirs. */
    private static boolean isAdmin(ServerCommandSource source) {
        return isWorldHost(source) || Permissions.check(source, ADMIN_NODE, 2);
    }

    /**
     * The person whose game this is: the player who opened the world, in singleplayer or on a LAN.
     *
     * <p>⚠️ Without this, {@code /companion reload} disappears in an ordinary survival singleplayer
     * world. "Allow Cheats" defaults <b>off</b> there, which means permission level <b>0</b> — so an
     * operator-level check refuses the one person who owns the game, the config file and the machine
     * it runs on. The command tree used to carry {@code isSingleplayer() ||} at its root for exactly
     * this reason; splitting permissions per node dropped that clause along with the root gate, and
     * the failure is invisible until someone edits their config and tries to reload it.
     *
     * <p>Asks who the <b>host</b> is rather than whether the server is singleplayer, which is the one
     * improvement on the original. An integrated server opened to LAN still reports itself as
     * singleplayer, so the old test would have handed operator rights to every guest who joined.
     */
    private static boolean isWorldHost(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        ServerPlayerEntity player = source.getPlayer();
        if (server == null || player == null) {
            return false;
        }
        return server.isHost(player.getGameProfile());
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("companion")
                        // Deliberately no gate on the root. Permissions are per-node now, and a root
                        // requirement would silently veto every one of them — which is exactly what
                        // it was doing.
                        .then(CommandManager.literal("spawn").requires(player("spawn"))
                                .executes(ctx -> spawn(ctx.getSource(), null))
                                .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                        .suggests(ROSTER_SUGGESTIONS)
                                        .executes(ctx -> spawn(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(CommandManager.literal("goto").requires(player("goto"))
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
                        .then(CommandManager.literal("remember").requires(player("remember"))
                                .then(CommandManager.argument("fact", StringArgumentType.greedyString())
                                        .executes(ctx -> remember(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "fact"), false))))
                        .then(CommandManager.literal("rememberhere").requires(player("rememberhere"))
                                .then(CommandManager.argument("fact", StringArgumentType.greedyString())
                                        .executes(ctx -> remember(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "fact"), true))))
                        .then(CommandManager.literal("list").requires(player("list"))
                                .executes(ctx -> list(ctx.getSource())))
                        // Open to everyone, and split inside: reload does the caller's OWN half
                        // unconditionally (their file, their endpoints, nobody else affected) and
                        // the SERVER's half only for an operator. Gating the whole command left a
                        // non-operator with no way to apply an edit to settings only they read.
                        .then(CommandManager.literal("reload").requires(player("reload"))
                                .executes(ctx -> reload(ctx.getSource())))
                        .then(CommandManager.literal("config").requires(player("config"))
                                .executes(ctx -> config(ctx.getSource())))
                        .then(CommandManager.literal("radar").requires(player("radar"))
                                .executes(ctx -> radar(ctx.getSource())))
                        .then(CommandManager.literal("hud").requires(player("hud"))
                                .executes(ctx -> hud(ctx.getSource())))
                        .then(CommandManager.literal("tokens").requires(player("tokens"))
                                .executes(ctx -> tokens(ctx.getSource())))
                        .then(CommandManager.literal("skills").requires(player("skills"))
                                .executes(ctx -> skills(ctx.getSource()))
                            // Overwrites files in the server's skills directory, so operator-only
                            // even though listing them is not.
                            .then(CommandManager.literal("reset").requires(operator("skills.reset"))
                                    .executes(ctx -> skillsReset(ctx.getSource(), null))
                                    .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                            .suggests(BUNDLED_SUGGESTIONS)
                                            .executes(ctx -> skillsReset(ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "name"))))))
                        .then(CommandManager.literal("skill").requires(player("skill"))
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
                .requires(player(literal))
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
     * The companions in this world that the caller may act on: their own, or every one for an
     * operator.
     *
     * <p>⚠️ <b>This is the ownership check the whole command surface was missing.</b> Every
     * targeting command used to go through a lookup that matched on display name alone, so opening
     * the commands to ordinary players would have let anybody move, inspect or <b>despawn</b>
     * somebody else's companion. The owner was recorded at spawn all along; it was simply never
     * consulted.
     *
     * <p>An ownerless companion — spawned from the console, so {@code initBrain} never ran — is
     * visible only to an operator. Letting whoever walks past claim it is how a companion nobody
     * owns becomes a companion everybody owns.
     */
    private static List<CompanionEntity> ownedCompanions(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            // Console or a command block: it owns nothing, so "yours" is meaningless. An operator
            // source sees everything; anything else sees nothing.
            return isAdmin(source) ? liveCompanions(source) : List.of();
        }
        // ⚠️ Strictly the caller's own, INCLUDING for operators. This used to hand an admin the
        // full list, which quietly made every unnamed command target the nearest companion of
        // anybody — so an opped player standing near someone else's companion could delete it with
        // a bare "/companion despawn" and no warning. That is the same coin-flip targeting the
        // roster work removed, reintroduced for exactly the people most likely to be standing
        // around other players' companions.
        //
        // It also matters because a family server ops everyone: if admin widened the default,
        // ownership would be enforced against almost nobody. Reaching another player's companion is
        // still possible, but it has to be asked for by name — see findCompanion.
        UUID me = player.getUuid();
        return liveCompanions(source).stream().filter(c -> me.equals(c.getOwnerUuid())).toList();
    }

    /**
     * Find one of the caller's own companions by name, anywhere on the server, in any world.
     *
     * <p>Only used by {@code spawn}'s duplicate check. Everything else is deliberately scoped to the
     * caller's world — you cannot send a command to a companion in the Nether from the Overworld, and
     * pretending otherwise would just move the failure somewhere less obvious.
     *
     * <p>⚠️ <b>Scoped to one owner, which it was not before.</b> Matching by name across the whole
     * server meant one roster entry was one companion for the entire server: the second player to
     * try {@code /companion spawn Vetta} was told Vetta was already out, four thousand blocks away,
     * in somebody else's base. Now that each client brings its own roster, two players having a
     * companion with the same name is the ordinary case and not a collision — ownership is what
     * tells them apart.
     */
    private static CompanionEntity findOwnedAnywhere(MinecraftServer server, UUID owner, String name) {
        if (server == null || name == null || owner == null) {
            return null;
        }
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof CompanionEntity companion
                        && owner.equals(companion.getOwnerUuid())
                        && companion.displayName().equalsIgnoreCase(name)) {
                    return companion;
                }
            }
        }
        return null;
    }

    /** How many companions this player has out, across every world. */
    private static int countOwnedAnywhere(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) {
            return 0;
        }
        int count = 0;
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof CompanionEntity companion
                        && owner.equals(companion.getOwnerUuid())) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Companions whose owner is not online, and which are therefore doing nothing for anybody.
     *
     * <p>Only used to explain a refusal. They still count against the cap — see the note where that
     * refusal is raised for why not counting them is the worse of the two options.
     */
    private static int countAbandoned(MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        int count = 0;
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof CompanionEntity companion) {
                    UUID owner = companion.getOwnerUuid();
                    if (owner == null || server.getPlayerManager().getPlayer(owner) == null) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** How many companions exist on the whole server — the global cap's input. */
    private static int countAllAnywhere(MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        int count = 0;
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof CompanionEntity) {
                    count++;
                }
            }
        }
        return count;
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
        // Only ever the caller's own — see ownedCompanions. Nothing below can return somebody
        // else's companion, which is the property that makes the level-0 permissions safe.
        List<CompanionEntity> companions = ownedCompanions(source);
        if (companions.isEmpty()) {
            return null;
        }
        if (name != null && !name.isBlank()) {
            String wanted = name.strip();
            CompanionEntity own = companions.stream()
                    .filter(c -> c.displayName().equalsIgnoreCase(wanted))
                    .findFirst()
                    .orElse(null);
            if (own != null) {
                return own;
            }
            return adminReach(source, wanted);
        }
        // Nearest of yours: the list is already distance-sorted, and it no longer needs a
        // second pass to prefer your own because it holds nothing else. The old fallback to
        // companions.get(0) — anybody's nearest — is deliberately gone.
        return companions.get(0);
    }

    /**
     * An operator reaching a companion that is not theirs, by name and on purpose.
     *
     * <p>The escape hatch that keeps a server administrable: someone has to be able to clear up a
     * companion whose owner has gone. It is deliberately reachable <b>only by name</b> — an unnamed
     * command never leaves the caller's own companions — so acting on someone else's is always
     * something that was typed out, never something that happened because of where you were standing.
     *
     * <p>⚠️ Announces itself. Sending feedback from a lookup is not tidy, but the alternative is
     * worse: this is the one path where a command does something to another player's property, and
     * every caller would otherwise have to remember to say so. One place that cannot be forgotten
     * beats six that can.
     */
    private static CompanionEntity adminReach(ServerCommandSource source, String wanted) {
        if (!isAdmin(source)) {
            return null;
        }
        CompanionEntity other = liveCompanions(source).stream()
                .filter(c -> c.displayName().equalsIgnoreCase(wanted))
                .findFirst()
                .orElse(null);
        if (other == null) {
            return null;
        }
        String owner = other.ownerName();
        source.sendFeedback(() -> Text.literal(
                other.displayName() + " belongs to " + owner + " — acting on it as an operator.")
                .formatted(Formatting.YELLOW), false);
        AiCompanion.LOGGER.info("[{}] {} acted on {}'s companion {} as an operator",
                AiCompanion.MOD_ID, source.getName(), owner, other.displayName());
        return other;
    }

    /**
     * Report that no companion matched, naming what is actually out there.
     *
     * <p>"No companion found nearby" was the same message whether none existed, one was in an
     * unloaded chunk, or the name was simply misspelt — three different problems with three different
     * fixes. Listing the live ones distinguishes them at a glance.
     */
    private static int noCompanion(ServerCommandSource source, String name) {
        // "It belongs to someone else" is a fourth cause, and the one that would otherwise read as
        // "no companion called Vetta" while Vetta is standing in front of you. Checked first,
        // against every companion in the world rather than only the caller's.
        if (name != null && !name.isBlank()) {
            String wanted = name.strip();
            CompanionEntity other = liveCompanions(source).stream()
                    .filter(c -> c.displayName().equalsIgnoreCase(wanted))
                    .findFirst()
                    .orElse(null);
            if (other != null) {
                source.sendError(Text.literal(
                        other.displayName() + " belongs to " + other.ownerName() + "."));
                return 0;
            }
        }
        List<CompanionEntity> companions = ownedCompanions(source);
        if (companions.isEmpty()) {
            source.sendError(Text.literal(
                    "You have no companion out (none spawned, or it drifted into an unloaded area). "
                            + "/companion spawn to call one."));
            return 0;
        }
        String live = companions.stream().map(CompanionEntity::displayName)
                .collect(java.util.stream.Collectors.joining(", "));
        source.sendError(Text.literal("You have no companion called '" + name.strip()
                + "'. Yours right now: " + live));
        return 0;
    }

    /**
     * Tab-completion over the identities the CALLER's client announced — what {@code spawn} accepts
     * from them. Falls back to the server's own roster for the console and singleplayer.
     */
    private static final SuggestionProvider<ServerCommandSource> ROSTER_SUGGESTIONS = (ctx, builder) -> {
        ServerPlayerEntity caller = ctx.getSource().getPlayer();
        for (CompanionConfig.RosterEntry entry
                : ClientProfiles.rosterFor(caller == null ? null : caller.getUuid())) {
            builder.suggest(entry.name());
        }
        return builder.buildFuture();
    };

    /** Tab-completion over the companions actually in the world — what the targeting commands accept. */
    private static final SuggestionProvider<ServerCommandSource> LIVE_SUGGESTIONS = (ctx, builder) -> {
        for (CompanionEntity companion : ownedCompanions(ctx.getSource())) {
            builder.suggest(companion.displayName());
        }
        return builder.buildFuture();
    };

    /**
     * Recall the companion to the caller — interrupts whatever it was doing and paths back.
     *
     * <p>Or teleports, when walking is impossible. A companion outside the players' simulation distance
     * receives no ticks, so it cannot run a task, so it cannot walk anywhere: this command would set a
     * pathfinding goal, report "coming to …", and do nothing at all. That is not a slow recall, it is a
     * companion that is never coming back, and the only escape was to despawn it. Since this is the
     * command an owner reaches for precisely when a companion has gone too far, it has to work at any
     * distance — arriving is the contract and walking is the flavour.
     */
    private static int come(ServerCommandSource source, String name) {
        CompanionEntity companion = findCompanion(source, name);
        if (companion == null) {
            return noCompanion(source, name);
        }
        ServerPlayerEntity player = source.getPlayer();
        BlockPos target = player != null ? player.getBlockPos() : companion.getBlockPos();
        String who = companion.displayName();

        boolean stranded = !companion.isTicking();
        if (stranded) {
            // Teleport BEFORE handing over a task. Arriving next to the owner is what puts the companion
            // back inside the simulated area, and only then can anything it is asked to do actually run.
            double distance = player != null ? Math.sqrt(companion.squaredDistanceTo(player)) : -1;
            long idleMs = companion.millisSinceTick();
            AiCompanion.LOGGER.warn("[{}] {} has not ticked for {} ms at {} blocks — outside simulation "
                    + "distance, so it cannot walk back. Teleporting instead of pathing.",
                    AiCompanion.MOD_ID, who, idleMs, String.format("%.0f", distance));
            companion.teleport(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, false);
        }

        AltoClefController ctrl = companion.getController();
        if (ctrl != null) {
            // Controller-aware: replaces the current task so it stops "running off" and comes back.
            ctrl.runUserTask(new GetToBlockTask(target));
        } else {
            companion.goTo(target);
        }
        source.sendFeedback(() -> Text.literal(stranded
                ? who + " was too far away to walk back and has been brought to "
                        + target.toShortString()
                : who + " coming to " + target.toShortString()), false);
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

        // ⚠️ When the owning client holds the corpus, the write belongs THERE.
        //
        // These commands are Brigadier and run on the server, so they never went through the
        // BrainTransport seam that moved everything else. Observed 2026-08-20 with clientBrain on:
        // conversational memories went to the client while /companion rememberhere wrote to the
        // server. The corpus was then split across two machines, and asking where home was recalled
        // nothing at all — with no error, because a recall that finds nothing looks exactly like a
        // recall that found nothing.
        //
        // The client prints its own confirmation, from the record it actually stored. It is also the
        // only side that can count the corpus once it owns it.
        if (adris.altoclef.player2api.brain.NetworkBrainTransport.canThink(owner)) {
            try {
                com.google.gson.JsonObject request = adris.altoclef.player2api.brain.BrainWire
                        .rememberRequest(fact.strip(), thisWorldOnly, worldId,
                                place == null ? null : place.dimension(),
                                place == null ? null : place.x(),
                                place == null ? null : place.y(),
                                place == null ? null : place.z());
                net.minecraft.network.PacketByteBuf buf = PacketByteBufs.create();
                adris.altoclef.player2api.brain.BrainWire.writeRemember(buf, request);
                ServerPlayNetworking.send(player,
                        adris.altoclef.player2api.brain.BrainWire.MEMORY_REMEMBER, buf);
                return 1;
            } catch (Throwable e) {
                // Falling through to the server-side write would put the memory on the wrong
                // machine, which is the bug. Saying so is the honest outcome.
                source.sendError(Text.literal(
                        "Could not reach your client to store that: " + e));
                AiCompanion.LOGGER.warn("[{}] could not send a remember to {}", AiCompanion.MOD_ID,
                        owner, e);
                return 0;
            }
        }

        CompletableFuture.runAsync(() -> {
            try {
                // The record as STORED, not as submitted. Reporting the position we captured would
                // claim success even when the store kept an older record and dropped it — which is
                // exactly what happened the first time this shipped.
                com.neovetta.aicompanion.memory.MemoryRecord saved =
                        adris.altoclef.player2api.CompanionMemory.remember(owner, fact.strip(),
                        thisWorldOnly
                                ? com.neovetta.aicompanion.memory.MemoryScope.WORLD
                                : com.neovetta.aicompanion.memory.MemoryScope.PERSON,
                        worldId, place);
                int held = adris.altoclef.player2api.CompanionMemory.countFor(owner);
                final String where = saved.place() == null ? ""
                        : "  @ " + saved.place().x() + ", " + saved.place().y()
                                + ", " + saved.place().z();
                // Back to the server thread to talk: sendFeedback is not safe off it.
                server.execute(() -> source.sendFeedback(() -> Text.literal(
                        (thisWorldOnly
                                ? "Remembered, here in this world: "
                                : "Remembered: ")
                                + fact.strip() + where)
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
        // Yours, or everyone's for an operator. Listing every companion within a 20000-block box to
        // any player is both spam and a position readout for somebody else's base.
        List<CompanionEntity> companions = ownedCompanions(source);
        if (companions.isEmpty()) {
            source.sendFeedback(() -> Text.literal(
                    "You have no companions out. /companion spawn to call one.")
                    .formatted(Formatting.GRAY), false);
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
        // The caller's own machine first, and without asking anyone's permission: these are the
        // settings only that client ever reads, and on a dedicated server the server has never even
        // seen the file they live in. Skipped for the world host, where the client and the server
        // are one JVM and reloadAndApply below already re-reads the very same file.
        final ServerPlayerEntity caller = source.getPlayer();
        boolean toldClient = false;
        if (caller != null && !isWorldHost(source)
                && ServerPlayNetworking.canSend(caller, AiCompanion.RELOAD_CLIENT_CONFIG)) {
            ServerPlayNetworking.send(caller, AiCompanion.RELOAD_CLIENT_CONFIG, PacketByteBufs.create());
            toldClient = true;
        }

        if (!isAdmin(source)) {
            // Not an error: they reloaded everything that was theirs to reload. Saying which half ran
            // matters, because the half that did not is the one an operator would have expected.
            if (toldClient) {
                source.sendFeedback(() -> Text.literal(
                        "Reloading your own settings — your companions, your endpoints, your memory "
                                + "switches. This server's rules are the operator's and are unchanged.")
                        .formatted(Formatting.GREEN), false);
                return 1;
            }
            source.sendError(Text.literal(
                    "Nothing to reload here: this server's config is the operator's, and your client "
                            + "did not answer. Update the mod, or edit your own config in "
                            + "/companion config."));
            return 0;
        }

        final int count = CompanionConfig.reloadAndApply(source.getServer());
        final int skillCount = CompanionSkills.all().size();
        source.sendFeedback(() -> Text.literal(String.format(
                "Config reloaded. LLM/TTS/behavior settings apply from the next reply; persona re-applied to %d live companion(s); %d skill(s) loaded.",
                count, skillCount)), false);
        source.sendFeedback(() -> Text.literal(
                "Note: name/description/skin changes need /companion despawn + /companion spawn."), false);
        // Whatever reloadAndApply just found out about memory, said here rather than saved for the
        // next conversation turn. Someone who ran this command has usually just changed a memory or
        // embeddings setting, and this is the moment they are waiting to hear whether it took.
        // Silent when memory is off or nothing changed, which is almost always.
        for (adris.altoclef.player2api.MemoryHealth.Notice notice
                : adris.altoclef.player2api.MemoryHealth.drain()) {
            source.sendFeedback(() -> Text.literal(notice.text())
                    .formatted(notice.problem() ? Formatting.RED : Formatting.GREEN), false);
        }
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
        ServerPlayerEntity player = source.getPlayer();
        final UUID owner = player == null ? null : player.getUuid();
        MinecraftServer server = source.getServer();

        // The caps first, before any lookup: refusing after resolving an identity would be the same
        // answer at more cost, and the global one is a TPS brake that should bite immediately.
        if (owner != null && !isAdmin(source)) {
            int mine = countOwnedAnywhere(server, owner);
            if (!ServerPolicy.withinCap(mine, ServerPolicy.maxCompanionsPerPlayer)) {
                source.sendError(Text.literal("You already have " + mine + " companion(s) out, which is "
                        + "this server's limit. /companion despawn to put one away."));
                return 0;
            }
        }
        if (!isAdmin(source)
                && !ServerPolicy.withinCap(countAllAnywhere(server), ServerPolicy.globalCompanionCap)) {
            // ⚠️ Abandoned companions are counted deliberately. The cap reads as a TPS brake, and
            // after the brainless-tick guard an offline player's companion is no longer a pathfinder
            // — so the tempting fix is to stop counting them. That trades a visible, bounded problem
            // for an invisible, unbounded one: companions are saved with the world, nothing else
            // limits how many accumulate, and a server would quietly collect hundreds of idle bodies
            // over a few months of players spawning two and never coming back.
            //
            // So the count stays honest and the REFUSAL explains itself instead. "At the limit" with
            // no further detail is a dead end for whoever hits it; naming how many belong to players
            // who are not here turns it into something an operator can act on.
            int abandoned = countAbandoned(server);
            String detail = abandoned == 0 ? ""
                    : " " + abandoned + " of them belong to players who are offline";
            source.sendError(Text.literal("This server is at its limit of "
                    + ServerPolicy.globalCompanionCap + " companions." + detail
                    + ". Try again when someone despawns one, or ask an operator."));
            return 0;
        }

        // The CALLER's roster, announced by their client, falling back to the server's own for the
        // console and for singleplayer. This is what makes identity the player's to choose: on a
        // dedicated server the file next to the world is the operator's, and reading it here is how
        // every player ended up with the operator's companion.
        List<CompanionConfig.RosterEntry> available = ClientProfiles.rosterFor(owner);

        CompanionConfig.RosterEntry entry;
        if (requested == null || requested.isBlank()) {
            // The first configured companion the caller does not already have out, so spawning twice
            // gives you two without having to name either. Always taking the FIRST entry meant a
            // second bare spawn could only ever be refused.
            entry = available.stream()
                    .filter(e -> findOwnedAnywhere(server, owner, e.name()) == null)
                    .findFirst()
                    .orElse(null);
            if (entry == null) {
                int count = available.size();
                source.sendError(Text.literal(count == 1
                        ? "Your only configured companion is already out. /companion list to find them, "
                                + "or add another under \"companions\" in /companion config."
                        : "All " + count + " of your configured companions are already out. "
                                + "/companion list to find them, or add another in /companion config."));
                return 0;
            }
        } else {
            String wanted = requested.strip();
            entry = available.stream()
                    .filter(e -> e.name().equalsIgnoreCase(wanted))
                    .findFirst()
                    .orElse(null);
            if (entry == null) {
                String known = available.stream()
                        .map(CompanionConfig.RosterEntry::name)
                        .collect(java.util.stream.Collectors.joining(", "));
                source.sendError(Text.literal("No companion called '" + wanted
                        + "' in your config. Configured: " + known
                        + " — add another in /companion config, Companions tab."));
                return 0;
            }
            // Across every world, not just this one: a companion sent to the Nether is still out, and
            // spawning its double in the Overworld is exactly the duplicate this check exists to stop.
            // Scoped to the caller, so another player having a companion by this name is not your
            // problem and does not block you.
            CompanionEntity existing = findOwnedAnywhere(server, owner, entry.name());
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
        float yaw = player != null ? player.getYaw() : 0f;

        CompanionEntity companion = new CompanionEntity(AiCompanion.COMPANION, world);
        companion.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
        // Name, skin and the whole identity, persisted in NBT rather than looked up by name later:
        // a client-owned identity has to survive its owner logging off, and re-resolving it from the
        // server's roster is how a companion would silently turn back into the operator's.
        companion.applyRosterEntry(entry);
        world.spawnEntity(companion);

        // Attach the agent brain (owned by the spawning player). Talk to it in chat when nearby.
        // Identity is the caller's own, from the roster their client announced; llm/memory settings
        // are read on whichever machine ends up doing the thinking.
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
