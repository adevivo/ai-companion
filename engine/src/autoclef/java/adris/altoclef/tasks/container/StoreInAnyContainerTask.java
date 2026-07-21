package adris.altoclef.tasks.container;

import adris.altoclef.AltoClefController;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.storage.ContainerCache;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.WorldHelper;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class StoreInAnyContainerTask extends Task {
   private final ItemTarget[] toStore;
   private final boolean getIfNotPresent;

   public StoreInAnyContainerTask(boolean getIfNotPresent, ItemTarget... toStore) {
      this.getIfNotPresent = getIfNotPresent;
      this.toStore = toStore;
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      ItemTarget[] itemsToStore = this.getItemsToStore(this.controller);
      if (itemsToStore.length == 0) {
         return null;
      } else {
         if (this.getIfNotPresent) {
            for (ItemTarget target : this.toStore) {
               if (this.controller.getItemStorage().getItemCount(target) < target.getTargetCount()) {
                  this.setDebugState("Collecting " + target + " before storing.");
                  return TaskCatalogue.getItemTask(target);
               }
            }
         }

         Predicate<BlockPos> isValidContainer = pos -> {
            if (WorldHelper.isChest(this.controller, pos)
               && WorldHelper.isSolidBlock(this.controller, pos.above())
               && !WorldHelper.canBreak(this.controller, pos.above())) {
               return false;
            } else {
               Optional<ContainerCache> cache = this.controller.getItemStorage().getContainerAtPosition(pos);
               if (cache.isPresent() && cache.get().isFull()) {
                  return false;
               } else {
                  return WorldHelper.isChest(this.controller, pos) && this.controller.getModSettings().shouldAvoidSearchingForDungeonChests()
                     ? !this.isDungeonChest(this.controller, pos)
                     : true;
               }
            }
         };
         Optional<BlockPos> closestContainer = this.controller.getBlockScanner().getNearestBlock(isValidContainer, StoreInContainerTask.CONTAINER_BLOCKS);
         if (closestContainer.isPresent()) {
            this.setDebugState("Found a container; storing items.");
            return new StoreInContainerTask(closestContainer.get(), false, itemsToStore);
         } else {
            for (Block containerBlock : StoreInContainerTask.CONTAINER_BLOCKS) {
               if (this.controller.getItemStorage().hasItem(containerBlock.asItem())) {
                  this.setDebugState("Placing a container nearby.");
                  return new PlaceBlockNearbyTask(
                     pos -> !WorldHelper.isChest(this.controller, pos) || WorldHelper.isAir(this.controller, pos.above()), containerBlock
                  );
               }
            }

            this.setDebugState("Obtaining a chest to store items.");
            return TaskCatalogue.getItemTask(Items.CHEST, 1);
         }
      }
   }

   @Override
   public boolean isFinished() {
      return this.getItemsToStore(this.controller).length == 0;
   }

   private ItemTarget[] getItemsToStore(AltoClefController controller) {
      return Arrays.stream(this.toStore).filter(target -> controller.getItemStorage().hasItem(target.getMatches())).toArray(ItemTarget[]::new);
   }

   private boolean isDungeonChest(AltoClefController controller, BlockPos pos) {
      int range = 6;

      for (int dx = -range; dx <= range; dx++) {
         for (int dz = -range; dz <= range; dz++) {
            if (controller.getWorld().getBlockState(pos.offset(dx, 0, dz)).is(Blocks.SPAWNER)) {
               return true;
            }
         }
      }

      return false;
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof StoreInAnyContainerTask task)
         ? false
         : task.getIfNotPresent == this.getIfNotPresent && Arrays.equals((Object[])task.toStore, (Object[])this.toStore);
   }

   @Override
   protected String toDebugString() {
      return "Storing in any container: " + Arrays.toString((Object[])this.toStore);
   }
}
