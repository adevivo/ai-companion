package com.player2.playerengine.automaton.utils;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Doors and gates a companion opened, so it can close them behind it.
 *
 * <p>Open it, walk through, close it: what a considerate player does. Only a door or gate the
 * companion itself opened from closed is tracked, so one somebody propped open, or opened for it, is
 * left as it was found.
 *
 * <p>It is closed once the companion is {@link #CLEAR_DISTANCE} blocks from it and nothing is standing
 * in the doorway, so it does not shut on the owner following behind. A door the companion is still
 * near is simply waited on; one it has walked far from without passing is given up after
 * {@link #GIVE_UP_MILLIS}, in case it was opened for a route that was then abandoned.
 */
public final class OpenedDoors {
   private static final double CLEAR_DISTANCE = 2.5;
   private static final long GIVE_UP_MILLIS = 60_000L;

   /** Per companion: door or gate position (the lower half for a door) and when it was opened. */
   private static final Map<UUID, Map<BlockPos, Long>> OPENED = new ConcurrentHashMap<>();

   private OpenedDoors() {
   }

   /** A wooden door or a fence gate that is currently shut: the ones worth closing again. */
   public static boolean isClosedDoorOrGate(BlockState state) {
      if (state.getBlock() instanceof DoorBlock door) {
         return DoorBlock.isWoodenDoor(state) && !door.isOpen(state);
      }
      return state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN);
   }

   /** The companion just opened this, from closed. */
   public static void opened(LivingEntity entity, BlockPos pos) {
      BlockState state = entity.level().getBlockState(pos);
      BlockPos key = pos;
      if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
         key = pos.below();
      }
      OPENED.computeIfAbsent(entity.getUUID(), k -> new ConcurrentHashMap<>()).put(key.immutable(), System.currentTimeMillis());
   }

   /** Close whatever this companion has walked clear of. Called every tick from its controller. */
   public static void tick(LivingEntity entity) {
      Map<BlockPos, Long> mine = OPENED.get(entity.getUUID());
      if (mine == null || mine.isEmpty()) {
         return;
      }
      Level level = entity.level();
      long now = System.currentTimeMillis();
      for (Iterator<Map.Entry<BlockPos, Long>> it = mine.entrySet().iterator(); it.hasNext(); ) {
         Map.Entry<BlockPos, Long> e = it.next();
         BlockPos pos = e.getKey();
         if (!level.isLoaded(pos)) {
            it.remove();
            continue;
         }
         BlockState state = level.getBlockState(pos);
         boolean stillOurs = (state.getBlock() instanceof DoorBlock door && door.isOpen(state))
            || (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN));
         if (!stillOurs) {
            it.remove(); // broken, replaced, or somebody closed it already
            continue;
         }
         if (pos.getCenter().distanceTo(entity.position()) < CLEAR_DISTANCE) {
            e.setValue(now); // still in or beside the doorway: keep waiting
            continue;
         }
         if (now - e.getValue() > GIVE_UP_MILLIS) {
            it.remove();
            continue;
         }
         if (occupied(level, pos, state)) {
            continue; // somebody is in the doorway; do not shut it on them
         }
         close(entity, level, pos, state);
         it.remove();
      }
   }

   /** Positions belong to one world; drop them all when it stops. */
   public static void clearAll() {
      OPENED.clear();
   }

   private static boolean occupied(Level level, BlockPos pos, BlockState state) {
      AABB doorway = new AABB(pos).expandTowards(0, state.getBlock() instanceof DoorBlock ? 1 : 0, 0);
      // Living things only: a dropped item in the doorway is no reason to leave it open.
      return !level.getEntitiesOfClass(LivingEntity.class, doorway, Entity::isAlive).isEmpty();
   }

   private static void close(LivingEntity entity, Level level, BlockPos pos, BlockState state) {
      if (state.getBlock() instanceof DoorBlock door) {
         // Handles both halves, the sound and the game event, as a player closing it would.
         door.setOpen(entity, level, state, pos, false);
      } else {
         level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, false), 10);
         level.playSound(null, pos, SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
      }
   }
}
