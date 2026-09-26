package com.player2.playerengine.util.helpers;

import com.mojang.serialization.Codec;
import dev.architectury.event.events.common.InteractionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Which storage containers each owner has let their companions use.
 *
 * <h2>Why</h2>
 *
 * Minecraft does not record who placed a chest, so "every container within 16 blocks" included other
 * players' chests, and in a team event another team's. A companion takes only from containers its
 * owner has allowed. An owner allows one by:
 *
 * <ul>
 *   <li><b>opening it themselves.</b> A container you can open is one you could empty yourself, so
 *       your companion using it gives you nothing you did not already have;</li>
 *   <li><b>asking for it</b> ({@code usechest} while they stand at it); or</li>
 *   <li><b>having the companion place it</b>, which a deposit does when there is no room.</li>
 * </ul>
 *
 * <p>Per OWNER rather than per companion, so all of one player's companions share the list, and
 * persistent, in {@code <save>/data/aicompanion_allowed_containers.dat}.
 *
 * <p>This is deliberately plain data and not the memory system. Recall is fuzzy and lives on the
 * owner's client; a permission has to be an exact yes or no, checked on the server.
 */
public final class ChestPermissions extends SavedData {
   /** Becomes {@code <save>/data/aicompanion_allowed_containers.dat}. */
   private static final String DATA_NAME = "aicompanion_allowed_containers";

   static final Codec<ChestPermissions> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, GlobalPos.CODEC.listOf())
      .xmap(ChestPermissions::fromDisk, p -> {
         Map<UUID, List<GlobalPos>> out = new HashMap<>();
         p.allowed.forEach((owner, set) -> out.put(owner, new ArrayList<>(set)));
         return out;
      });

   /**
    * ⚠️ One shared instance, and a real data-fixer type. Same two 1.21.11 rules as {@code
    * WorldIdentity.TYPE}: a type rebuilt per call misses the storage cache and re-reads the file, and
    * a null fixer throws on the second load, once the file exists.
    */
   private static final SavedDataType<ChestPermissions> TYPE = new SavedDataType<>(
      DATA_NAME, ChestPermissions::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE
   );

   private final Map<UUID, Set<GlobalPos>> allowed = new HashMap<>();

   private ChestPermissions() {
   }

   private static ChestPermissions fromDisk(Map<UUID, List<GlobalPos>> stored) {
      ChestPermissions p = new ChestPermissions();
      // The decoded map is immutable; this one is added to.
      stored.forEach((owner, list) -> p.allowed.put(owner, new LinkedHashSet<>(list)));
      return p;
   }

   /** For tests: the live, mutable set for one owner, created if absent. */
   Set<GlobalPos> allowedFor(UUID owner) {
      return this.allowed.computeIfAbsent(owner, k -> new LinkedHashSet<>());
   }

   static ChestPermissions empty() {
      return new ChestPermissions();
   }

   private static ChestPermissions of(ServerLevel level) {
      return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
   }

   /**
    * The key a container is stored under: its dimension, and the first half of a double chest.
    * Checking uses either half (see {@link #isAllowed}), so a key that moves when a chest is extended
    * does not lose the permission.
    */
   private static GlobalPos key(Level level, BlockPos pos) {
      return GlobalPos.of(level.dimension(), ContainerAccess.canonical(level, pos));
   }

   /** Whether this owner has allowed the container at {@code pos}. False for no owner. */
   public static boolean isAllowed(Level level, UUID owner, BlockPos pos) {
      if (owner == null || !(level instanceof ServerLevel server)) {
         return false;
      }
      Set<GlobalPos> set = of(server).allowed.get(owner);
      if (set == null) {
         return false;
      }
      // Either half. A single chest allowed and then extended into a double chest is keyed by its
      // first half, which may now be the new one. Whoever extends a chest shares its contents anyway.
      for (BlockPos half : ContainerAccess.halves(level, pos)) {
         if (set.contains(GlobalPos.of(level.dimension(), half))) {
            return true;
         }
      }
      return false;
   }

   /** @return true when this is newly allowed, false when it already was or cannot be recorded */
   public static boolean allow(Level level, UUID owner, BlockPos pos) {
      if (owner == null || !(level instanceof ServerLevel server)) {
         return false;
      }
      ChestPermissions p = of(server);
      boolean added = p.allowed.computeIfAbsent(owner, k -> new LinkedHashSet<>()).add(key(level, pos));
      if (added) {
         p.setDirty();
      }
      return added;
   }

   /** @return true when it had been allowed and no longer is */
   public static boolean forget(Level level, UUID owner, BlockPos pos) {
      if (owner == null || !(level instanceof ServerLevel server)) {
         return false;
      }
      ChestPermissions p = of(server);
      Set<GlobalPos> set = p.allowed.get(owner);
      boolean removed = false;
      if (set != null) {
         // Both halves, for the same reason isAllowed checks both.
         for (BlockPos half : ContainerAccess.halves(level, pos)) {
            removed |= set.remove(GlobalPos.of(level.dimension(), half));
         }
      }
      if (removed) {
         p.setDirty();
      }
      return removed;
   }

   /**
    * The container the owner means by "this chest": the one they are looking at within reach, or else
    * the nearest one within {@link #NEAR_OWNER} blocks of where they stand.
    *
    * <p>Always found from the OWNER's position, never the companion's. That is what keeps the choice
    * the owner's: the model cannot allow a container its owner is not standing at, and nor can anyone
    * else talking to the companion.
    */
   public static Optional<BlockPos> containerAtOwner(Player owner) {
      Level level = owner.level();
      if (owner.pick(ContainerAccess.REACH + 0.5, 0.0F, false) instanceof BlockHitResult hit
         && hit.getType() == HitResult.Type.BLOCK
         && ContainerAccess.isStorage(level.getBlockEntity(hit.getBlockPos()))) {
         return Optional.of(hit.getBlockPos());
      }
      BlockPos at = owner.blockPosition();
      BlockPos best = null;
      double bestSq = Double.MAX_VALUE;
      for (BlockPos pos : BlockPos.betweenClosed(at.offset(-NEAR_OWNER, -NEAR_OWNER, -NEAR_OWNER), at.offset(NEAR_OWNER, NEAR_OWNER, NEAR_OWNER))) {
         if (ContainerAccess.isStorage(level.getBlockEntity(pos))) {
            double d = pos.getCenter().distanceToSqr(owner.position());
            if (d < bestSq) {
               bestSq = d;
               best = pos.immutable();
            }
         }
      }
      return Optional.ofNullable(best);
   }

   /** How close to the owner a container must be when they are not looking at one. */
   public static final int NEAR_OWNER = 3;

   /**
    * Opening a storage container allows it for the player who opened it. Registered once, from the
    * controller's static block.
    *
    * <p>A sneak-click with something in hand places a block against the container instead of opening
    * it, so that is not counted.
    */
   public static void registerEvents() {
      InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
         Level level = player.level();
         if (!level.isClientSide()
            && !(player.isShiftKeyDown() && !player.getItemInHand(hand).isEmpty())
            && ContainerAccess.isStorage(level.getBlockEntity(pos))) {
            allow(level, player.getUUID(), pos);
         }
         return InteractionResult.PASS;
      });
   }
}
