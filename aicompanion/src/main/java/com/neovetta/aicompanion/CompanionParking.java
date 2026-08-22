package com.neovetta.aicompanion;

import adris.altoclef.player2api.ServerPolicy;
import adris.altoclef.player2api.manager.ConversationManager;
import com.neovetta.aicompanion.entity.CompanionEntity;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * Putting a player's companions away while they are not here, and taking them out again when they
 * return.
 *
 * <h2>Why they cannot just stand there</h2>
 *
 * A companion is an entity in the world save, so one spawned months ago outlives the session, the
 * restart and the player. Observed on 2026-08-21: two companions belonging to an offline player were
 * standing in the world, drawn as default Steve to everyone else because their skins are a
 * client-side asset, unable to answer anybody, and — since a family server ops everyone — deletable
 * by whoever walked past. None of that is behaviour anyone chose; it is just what happens when
 * nothing ever puts them away.
 *
 * <p>They also accumulate. Every player who ever spawns one leaves it there, counting against the
 * server's companion cap, for ever.
 *
 * <h2>⚠️ The rule that matters: never lose a companion's inventory</h2>
 *
 * A companion can be carrying a full kit. So the order is <b>write, verify, then discard</b> — the
 * entity is only removed once its file has been written <em>and read back</em>. If anything fails at
 * any point, the companion is left standing exactly where it was.
 *
 * <p>That asymmetry is deliberate: a companion that failed to park is a nuisance and will be parked
 * on the next disconnect, while a companion that vanished with someone's diamonds is unforgivable
 * and unrecoverable. Every failure here is resolved in favour of leaving the entity alone.
 *
 * <p>A leftover file — from a crash between writing and discarding — is harmless. Restore skips any
 * companion whose UUID is already in the world, so the worst case is a file that is deleted without
 * being used.
 */
public final class CompanionParking {

    private CompanionParking() {}

    /** Where the parked companion actually was, so it comes back to the same place. */
    private static final String DIMENSION_KEY = "aicompanion:parked_dimension";

    private static Path parkedDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("aicompanion").resolve("parked");
    }

    private static Path parkedDir(UUID owner) {
        return parkedDir().resolve(owner.toString());
    }

    /**
     * Put away every companion belonging to this player. Called when they disconnect.
     *
     * <p>Runs on the server thread — it removes entities and touches world state.
     */
    public static void park(MinecraftServer server, UUID owner) {
        if (!ServerPolicy.parkWhenOwnerOffline || server == null || owner == null) {
            return;
        }
        int parked = 0;
        int failed = 0;
        for (ServerWorld world : server.getWorlds()) {
            // Copied first: discarding while iterating the world's entity view is asking for trouble.
            List<CompanionEntity> mine = new ArrayList<>();
            for (net.minecraft.entity.Entity entity : world.iterateEntities()) {
                if (entity instanceof CompanionEntity companion
                        && owner.equals(companion.getOwnerUuid())) {
                    mine.add(companion);
                }
            }
            for (CompanionEntity companion : mine) {
                if (parkOne(companion, world, owner)) {
                    parked++;
                } else {
                    failed++;
                }
            }
        }
        if (parked > 0 || failed > 0) {
            AiCompanion.LOGGER.info("[{}] parked {} companion(s) for {}{}", AiCompanion.MOD_ID,
                    parked, owner, failed == 0 ? "" : " (" + failed + " left standing — see above)");
        }
    }

    /** @return true only if the companion was safely stored AND removed. */
    private static boolean parkOne(CompanionEntity companion, ServerWorld world, UUID owner) {
        String name = companion.displayName();
        try {
            NbtCompound tag = new NbtCompound();
            // saveSelfNbt writes the entity id alongside its state, and returns false for anything
            // that must not be saved on its own (a passenger, something already removed). Believe it.
            if (!companion.saveSelfNbt(tag)) {
                AiCompanion.LOGGER.warn("[{}] not parking {} — it declined to be saved (riding "
                        + "something, or already gone). Leaving it where it is.",
                        AiCompanion.MOD_ID, name);
                return false;
            }
            tag.putString(DIMENSION_KEY, world.getRegistryKey().getValue().toString());

            Path dir = parkedDir(owner);
            Files.createDirectories(dir);
            File file = dir.resolve(companion.getUuid() + ".nbt").toFile();
            NbtIo.writeCompressed(tag, file);

            // ⚠️ Read it back before removing anything. A write that reported success but produced
            // an unreadable file is exactly the case that would cost somebody their inventory, and
            // it is cheap to rule out.
            NbtCompound check = NbtIo.readCompressed(file);
            if (check == null || !check.contains(DIMENSION_KEY)) {
                AiCompanion.LOGGER.error("[{}] parked file for {} did not read back — leaving the "
                        + "companion in the world rather than risk its inventory.",
                        AiCompanion.MOD_ID, name);
                return false;
            }

            // Only now. Its conversation state goes too, the same as a despawn — the manager keys on
            // the entity UUID and never cleans up on its own.
            ConversationManager.forget(companion.getUuid());
            companion.discard();
            return true;
        } catch (Throwable e) {
            AiCompanion.LOGGER.error("[{}] could not park {} — leaving it in the world",
                    AiCompanion.MOD_ID, name, e);
            return false;
        }
    }

    /**
     * Bring back everything this player had out. Called when they join.
     *
     * <p>Failure here is recoverable in a way parking is not: the file stays, and the next join tries
     * again. So a companion is never deleted from disk unless it was successfully put back.
     */
    public static void restore(MinecraftServer server, ServerPlayerEntity owner) {
        if (server == null || owner == null) {
            return;
        }
        Path dir = parkedDir(owner.getUuid());
        if (!Files.isDirectory(dir)) {
            return;
        }
        int restored = 0;
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".nbt")).toList()) {
                if (restoreOne(server, owner, file)) {
                    restored++;
                }
            }
        } catch (Throwable e) {
            AiCompanion.LOGGER.error("[{}] could not read parked companions for {}",
                    AiCompanion.MOD_ID, owner.getName().getString(), e);
        }
        if (restored > 0) {
            AiCompanion.LOGGER.info("[{}] restored {} parked companion(s) for {}",
                    AiCompanion.MOD_ID, restored, owner.getName().getString());
        }
    }

    private static boolean restoreOne(MinecraftServer server, ServerPlayerEntity owner, Path file) {
        try {
            NbtCompound tag = NbtIo.readCompressed(file.toFile());
            if (tag == null) {
                return false;
            }
            ServerWorld world = worldOf(server, tag);
            if (world == null) {
                // The dimension is gone — a datapack removed it, or the save moved. Keep the file:
                // deleting it is the one irreversible option, and the world may come back.
                AiCompanion.LOGGER.warn("[{}] parked companion in an unknown dimension ({}) — "
                        + "keeping it on disk", AiCompanion.MOD_ID, tag.getString(DIMENSION_KEY));
                return false;
            }

            CompanionEntity companion = new CompanionEntity(AiCompanion.COMPANION, world);
            companion.readNbt(tag); // position, rotation, UUID, inventory, identity, owner

            // A crash between writing the file and discarding the entity would leave both. Spawning
            // the second one is how an inventory gets duplicated, so the file loses.
            if (world.getEntity(companion.getUuid()) != null) {
                AiCompanion.LOGGER.info("[{}] {} is already in the world — dropping the stale parked "
                        + "copy", AiCompanion.MOD_ID, companion.displayName());
                Files.deleteIfExists(file);
                return false;
            }

            world.spawnEntity(companion);
            // Only once it is actually in the world.
            Files.deleteIfExists(file);
            return true;
        } catch (Throwable e) {
            AiCompanion.LOGGER.error("[{}] could not restore a parked companion from {} — the file "
                    + "is kept and will be retried on the next join", AiCompanion.MOD_ID, file, e);
            return false;
        }
    }

    private static ServerWorld worldOf(MinecraftServer server, NbtCompound tag) {
        String id = tag.getString(DIMENSION_KEY);
        if (id == null || id.isBlank()) {
            return server.getOverworld();
        }
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return server.getOverworld();
        }
        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, parsed);
        ServerWorld world = server.getWorld(key);
        return world;
    }

}
