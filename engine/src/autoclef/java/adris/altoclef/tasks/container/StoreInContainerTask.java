package adris.altoclef.tasks.container;

import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import baritone.api.entity.IInventoryProvider;
import baritone.api.entity.LivingEntityInventory;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

public class StoreInContainerTask extends Task {
   public static final Block[] CONTAINER_BLOCKS = Stream.concat(
         Arrays.stream(new Block[]{Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL}), Arrays.stream(ItemHelper.itemsToBlocks(ItemHelper.SHULKER_BOXES))
      )
      .toArray(Block[]::new);
   private final BlockPos containerPos;
   private final boolean getIfNotPresent;
   private final ItemTarget[] toStore;

   public StoreInContainerTask(BlockPos targetContainer, boolean getIfNotPresent, ItemTarget... toStore) {
      this.containerPos = targetContainer;
      this.getIfNotPresent = getIfNotPresent;
      this.toStore = toStore;
   }

   @Override
   protected void onStart() {
      for (ItemTarget target : this.toStore) {
         this.controller.getBehaviour().addProtectedItems(target.getMatches());
      }
   }

   @Override
   protected Task onTick() {
      if (this.isFinished()) {
         return null;
      } else {
         if (this.getIfNotPresent) {
            for (ItemTarget target : this.toStore) {
               int needed = target.getTargetCount();
               if (this.controller.getItemStorage().getItemCount(target) < needed) {
                  this.setDebugState("Collecting " + target + " first.");
                  return TaskCatalogue.getItemTask(target);
               }
            }
         }

         if (!this.containerPos
            .closerThan(
               new Vec3i(
                  (int)this.controller.getEntity().position().x, (int)this.controller.getEntity().position().y, (int)this.controller.getEntity().position().z
               ),
               4.5
            )) {
            this.setDebugState("Going to container");
            return new GetToBlockTask(this.containerPos);
         } else if (!(this.controller.getWorld().getBlockEntity(this.containerPos) instanceof RandomizableContainerBlockEntity container)) {
            Debug.logWarning("Block at " + this.containerPos + " is not a lootable container. Stopping.");
            return null;
         } else {
            RandomizableContainerBlockEntity var19 = container;
            LivingEntityInventory var20 = ((IInventoryProvider)this.controller.getEntity()).getLivingInventory();
            this.controller.getItemStorage().containers.WritableCache(this.controller, this.containerPos);
            this.setDebugState("Storing items");

            for (ItemTarget targetx : this.toStore) {
               int currentInContainer = this.countItem(var19, targetx);
               if (currentInContainer < targetx.getTargetCount()) {
                  int neededInContainer = targetx.getTargetCount() - currentInContainer;

                  for (int i = 0; i < var20.getContainerSize(); i++) {
                     ItemStack playerStack = var20.getItem(i);
                     if (targetx.matches(playerStack.getItem())) {
                        int toMove = Math.min(neededInContainer, playerStack.getCount());
                        ItemStack toInsert = playerStack.copy();
                        toInsert.setCount(toMove);
                        if (this.insertStack(var19, toInsert, true).getCount() != toInsert.getCount()) {
                           ItemStack remainder = this.insertStack(var19, toInsert, false);
                           int moved = toMove - remainder.getCount();
                           if (moved > 0) {
                              playerStack.shrink(moved);
                              var20.setItem(i, playerStack);
                              container.setChanged();
                              this.controller.getItemStorage().registerSlotAction();
                              return null;
                           }
                        }
                     }
                  }
               }
            }

            return null;
         }
      }
   }

   @Override
   public boolean isFinished() {
      return this.controller.getWorld().getBlockEntity(this.containerPos) instanceof Container containerInv
         ? Arrays.stream(this.toStore).allMatch(target -> this.countItem(containerInv, target) >= target.getTargetCount())
         : Arrays.stream(this.toStore).allMatch(target -> this.controller.getItemStorage().getItemCount(target) == 0);
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBehaviour().pop();
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof StoreInContainerTask task)
         ? false
         : Objects.equals(task.containerPos, this.containerPos)
            && task.getIfNotPresent == this.getIfNotPresent
            && Arrays.equals((Object[])task.toStore, (Object[])this.toStore);
   }

   @Override
   protected String toDebugString() {
      return "Storing in container[" + this.containerPos.toShortString() + "] " + Arrays.toString((Object[])this.toStore);
   }

   private int countItem(Container inventory, ItemTarget target) {
      int count = 0;

      for (int i = 0; i < inventory.getContainerSize(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (target.matches(stack.getItem())) {
            count += stack.getCount();
         }
      }

      return count;
   }

   private ItemStack insertStack(Container inventory, ItemStack stack, boolean simulate) {
      if (simulate) {
         stack = stack.copy();
      }

      for (int i = 0; i < inventory.getContainerSize() && !stack.isEmpty(); i++) {
         ItemStack slotStack = inventory.getItem(i);
         if (ItemStack.isSameItemSameTags(stack, slotStack)) {
            int space = slotStack.getMaxStackSize() - slotStack.getCount();
            int toTransfer = Math.min(stack.getCount(), space);
            if (toTransfer > 0) {
               slotStack.grow(toTransfer);
               stack.shrink(toTransfer);
               if (!simulate) {
                  inventory.setItem(i, slotStack);
               }
            }
         }
      }

      for (int ix = 0; ix < inventory.getContainerSize() && !stack.isEmpty(); ix++) {
         if (inventory.getItem(ix).isEmpty()) {
            if (!simulate) {
               inventory.setItem(ix, stack.copy());
            }

            stack.setCount(0);
         }
      }

      return stack;
   }
}
