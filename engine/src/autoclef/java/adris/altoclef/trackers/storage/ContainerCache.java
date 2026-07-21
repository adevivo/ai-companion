package adris.altoclef.trackers.storage;

import adris.altoclef.util.Dimension;
import java.util.HashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ContainerCache {
   private final BlockPos blockPos;
   private final Dimension dimension;
   private final ContainerType containerType;
   private final HashMap<Item, Integer> itemCounts = new HashMap<>();
   private int emptySlots;

   public ContainerCache(Dimension dimension, BlockPos blockPos, ContainerType containerType) {
      this.dimension = dimension;
      this.blockPos = blockPos;
      this.containerType = containerType;
   }

   public void update(Container screenHandler, Consumer<ItemStack> onStack) {
      this.itemCounts.clear();
      this.emptySlots = 0;
      int start = 0;
      int end = screenHandler.getContainerSize();
      boolean isFurnace = screenHandler instanceof FurnaceMenu;

      for (int i = start; i < end; i++) {
         ItemStack stack = screenHandler.getItem(i).copy();
         if (stack.isEmpty()) {
            if (!isFurnace || i != 2) {
               this.emptySlots++;
            }
         } else {
            Item item = stack.getItem();
            int count = stack.getCount();
            this.itemCounts.put(item, this.itemCounts.getOrDefault(item, 0) + count);
            onStack.accept(stack);
         }
      }
   }

   public int getItemCount(Item... items) {
      int result = 0;

      for (Item item : items) {
         result += this.itemCounts.getOrDefault(item, 0);
      }

      return result;
   }

   public boolean hasItem(Item... items) {
      for (Item item : items) {
         if (this.itemCounts.containsKey(item) && this.itemCounts.get(item) > 0) {
            return true;
         }
      }

      return false;
   }

   public int getEmptySlotCount() {
      return this.emptySlots;
   }

   public boolean isFull() {
      return this.emptySlots == 0;
   }

   public BlockPos getBlockPos() {
      return this.blockPos;
   }

   public ContainerType getContainerType() {
      return this.containerType;
   }

   public Dimension getDimension() {
      return this.dimension;
   }
}
