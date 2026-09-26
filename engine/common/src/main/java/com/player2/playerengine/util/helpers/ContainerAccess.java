package com.player2.playerengine.util.helpers;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Finding storage containers near a companion, reading them, and moving items in and out.
 *
 * <p>Written to replace the item moves in {@code StoreInContainerTask} and
 * {@code PickupFromContainerTask}, both of which duplicated items: one ran its "simulated" insert
 * against the chest's real stacks, the other inserted a probe item before the real move and never took
 * it back. Every move here takes from one side exactly what it gave to the other.
 *
 * <p>Storage only — chests (trapped included), barrels and shulker boxes. Furnaces, hoppers and
 * brewing stands are containers too, but "what is in the chests" never means them, and taking from a
 * furnace mid-smelt is not what anyone asked for.
 */
public final class ContainerAccess {
   /** How far to look for containers. Far enough to cover a base, near enough to walk every one. */
   public static final int SEARCH_RADIUS = 16;
   /** Within this distance of the block's centre the container can be used, as a player's reach. */
   public static final double REACH = 4.5;

   private ContainerAccess() {
   }

   public static boolean isStorage(BlockEntity blockEntity) {
      return blockEntity instanceof ChestBlockEntity
         || blockEntity instanceof BarrelBlockEntity
         || blockEntity instanceof ShulkerBoxBlockEntity;
   }

   /**
    * Storage containers within {@code radius} blocks, nearest first.
    *
    * <p>A double chest is ONE entry, keyed by its first half, so it is visited and reported once with
    * all 54 slots rather than as two chests of 27.
    */
   public static List<BlockPos> findNearby(PlayerEngineController mod, int radius) {
      Level level = mod.getWorld();
      Vec3 from = mod.getPlayer().position();
      BlockPos centre = mod.getPlayer().blockPosition();
      double maxSq = (double)radius * radius;
      Set<BlockPos> found = new LinkedHashSet<>();
      ChunkPos minChunk = new ChunkPos(centre.offset(-radius, 0, -radius));
      ChunkPos maxChunk = new ChunkPos(centre.offset(radius, 0, radius));

      for (int cx = minChunk.x; cx <= maxChunk.x; cx++) {
         for (int cz = minChunk.z; cz <= maxChunk.z; cz++) {
            // Loaded chunks only: this must never be the thing that generates or loads terrain.
            if (!level.hasChunk(cx, cz)) {
               continue;
            }
            LevelChunk chunk = level.getChunk(cx, cz);
            for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
               BlockPos pos = e.getKey();
               if (isStorage(e.getValue()) && pos.getCenter().distanceToSqr(from) <= maxSq) {
                  found.add(canonical(level, pos));
               }
            }
         }
      }

      List<BlockPos> result = new ArrayList<>(found);
      result.sort(Comparator.comparingDouble(p -> p.getCenter().distanceToSqr(from)));
      return result;
   }

   /** The position a double chest is known by — its first half — or the position itself. */
   public static BlockPos canonical(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof ChestBlock
         && ChestBlock.getBlockType(state) == DoubleBlockCombiner.BlockType.SECOND) {
         return pos.relative(ChestBlock.getConnectedDirection(state));
      }
      return pos;
   }

   /** Both halves of a double chest, or just {@code pos} for anything else. */
   public static List<BlockPos> halves(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof ChestBlock && ChestBlock.getBlockType(state) != DoubleBlockCombiner.BlockType.SINGLE) {
         return List.of(pos, pos.relative(ChestBlock.getConnectedDirection(state)));
      }
      return List.of(pos);
   }

   /** Whether {@code pos} is half of a double chest. */
   public static boolean isDouble(Level level, BlockPos pos) {
      return halves(level, pos).size() > 1;
   }

   /** The inventory at {@code pos}, with both halves of a double chest combined. */
   public static Optional<Container> open(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof ChestBlock chest) {
         // ignoreBlocked: a companion is not stopped by a block on the lid, which is a vanilla client
         // rule about the lid animation more than about access.
         return Optional.ofNullable(ChestBlock.getContainer(chest, state, level, pos, true));
      }
      BlockEntity blockEntity = level.getBlockEntity(pos);
      return isStorage(blockEntity) ? Optional.of((Container)blockEntity) : Optional.empty();
   }

   /** "chest", "double chest", "barrel", "shulker box" — how the container is named to the agent. */
   public static String describe(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof ChestBlock) {
         return ChestBlock.getBlockType(state) == DoubleBlockCombiner.BlockType.SINGLE ? "chest" : "double chest";
      }
      BlockEntity blockEntity = level.getBlockEntity(pos);
      if (blockEntity instanceof BarrelBlockEntity) {
         return "barrel";
      }
      return blockEntity instanceof ShulkerBoxBlockEntity ? "shulker box" : "container";
   }

   public static boolean inReach(PlayerEngineController mod, BlockPos pos) {
      return pos.getCenter().distanceToSqr(mod.getPlayer().position()) <= REACH * REACH;
   }

   /** Item name → total count, most plentiful first. */
   public static Map<String, Integer> summarize(Container container) {
      Map<String, Integer> counts = new LinkedHashMap<>();
      for (int i = 0; i < container.getContainerSize(); i++) {
         ItemStack stack = container.getItem(i);
         if (!stack.isEmpty()) {
            counts.merge(ItemHelper.stripItemName(stack.getItem()), stack.getCount(), Integer::sum);
         }
      }
      Map<String, Integer> sorted = new LinkedHashMap<>();
      counts.entrySet().stream()
         .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
         .forEachOrdered(e -> sorted.put(e.getKey(), e.getValue()));
      return sorted;
   }

   public static int count(Container container, Predicate<Item> match) {
      int total = 0;
      for (int i = 0; i < container.getContainerSize(); i++) {
         ItemStack stack = container.getItem(i);
         if (!stack.isEmpty() && match.test(stack.getItem())) {
            total += stack.getCount();
         }
      }
      return total;
   }

   /** Free slots in a container; a full container with some partial stacks still reads 0. */
   public static int freeSlots(Container container) {
      int free = 0;
      for (int i = 0; i < container.getContainerSize(); i++) {
         if (container.getItem(i).isEmpty()) {
            free++;
         }
      }
      return free;
   }

   /**
    * Move up to {@code max} matching items from the container into the companion's inventory.
    *
    * @return how many actually moved; less than asked when the container ran out or the inventory filled
    */
   public static int take(Container from, LivingEntityInventory to, Predicate<Item> match, int max) {
      int moved = 0;
      for (int i = 0; i < from.getContainerSize() && moved < max; i++) {
         ItemStack stack = from.getItem(i);
         if (stack.isEmpty() || !match.test(stack.getItem())) {
            continue;
         }
         int want = Math.min(stack.getCount(), max - moved);
         ItemStack toMove = stack.copyWithCount(want);
         // insertStack leaves the part that did not fit in toMove, so what was inserted is the difference.
         to.insertStack(toMove);
         int inserted = want - toMove.getCount();
         if (inserted > 0) {
            stack.shrink(inserted);
            from.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
            moved += inserted;
         }
         if (!toMove.isEmpty()) {
            break; // inventory full
         }
      }
      if (moved > 0) {
         from.setChanged();
      }
      return moved;
   }

   /**
    * Move up to {@code max} matching items from the companion's main inventory into the container.
    * Armour and offhand are never touched.
    *
    * @return how many actually moved; less than asked when the container filled or the items ran out
    */
   public static int put(LivingEntityInventory from, Container to, Predicate<ItemStack> match, int max) {
      int moved = 0;
      for (int i = 0; i < from.main.size() && moved < max; i++) {
         ItemStack source = from.main.get(i);
         if (source.isEmpty() || !match.test(source)) {
            continue;
         }
         int want = Math.min(source.getCount(), max - moved);
         int placed = insertInto(to, source, want);
         if (placed > 0) {
            source.shrink(placed);
            from.main.set(i, source.isEmpty() ? ItemStack.EMPTY : source);
            moved += placed;
         }
         if (placed < want) {
            break; // container full
         }
      }
      if (moved > 0) {
         to.setChanged();
      }
      return moved;
   }

   /** Place up to {@code count} of {@code source} into the container, topping up stacks first. Source is not changed. */
   private static int insertInto(Container to, ItemStack source, int count) {
      int left = count;
      for (int i = 0; i < to.getContainerSize() && left > 0; i++) {
         ItemStack slot = to.getItem(i);
         if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, source) && to.canPlaceItem(i, source)) {
            int room = Math.min(slot.getMaxStackSize(), to.getMaxStackSize()) - slot.getCount();
            int n = Math.min(room, left);
            if (n > 0) {
               slot.grow(n);
               to.setItem(i, slot);
               left -= n;
            }
         }
      }
      for (int i = 0; i < to.getContainerSize() && left > 0; i++) {
         if (to.getItem(i).isEmpty() && to.canPlaceItem(i, source)) {
            int n = Math.min(Math.min(source.getMaxStackSize(), to.getMaxStackSize()), left);
            to.setItem(i, source.copyWithCount(n));
            left -= n;
         }
      }
      return count - left;
   }
}
