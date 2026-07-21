package adris.altoclef.tasks.container;

import adris.altoclef.AltoClefController;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.movement.GetToXZTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.storage.ContainerCache;
import adris.altoclef.util.BlockRange;
import adris.altoclef.util.ItemTarget;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

public class StoreInStashTask extends Task {
   private final ItemTarget[] toStore;
   private final boolean getIfNotPresent;
   private final BlockRange stashRange;

   public StoreInStashTask(boolean getIfNotPresent, BlockRange stashRange, ItemTarget... toStore) {
      this.getIfNotPresent = getIfNotPresent;
      this.stashRange = stashRange;
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
                  this.setDebugState("Collecting " + target + " before stashing.");
                  return TaskCatalogue.getItemTask(target);
               }
            }
         }

         Optional<BlockPos> closestContainer = this.controller.getBlockScanner().getNearestBlock((Predicate<BlockPos>)(pos -> {
            if (!this.stashRange.contains(this.controller, pos)) {
               return false;
            } else {
               Optional<ContainerCache> cache = this.controller.getItemStorage().getContainerAtPosition(pos);
               return cache.<Boolean>map(containerCache -> !containerCache.isFull()).orElse(true);
            }
         }), StoreInContainerTask.CONTAINER_BLOCKS);
         if (closestContainer.isPresent()) {
            this.setDebugState("Storing in closest stash container.");
            return new StoreInContainerTask(closestContainer.get(), false, itemsToStore);
         } else if (!this.stashRange.contains(this.controller, this.controller.getEntity().blockPosition())) {
            this.setDebugState("Traveling to stash area.");
            BlockPos centerStash = this.stashRange.getCenter();
            return new GetToXZTask(centerStash.getX(), centerStash.getZ());
         } else {
            this.setDebugState("Inside stash, but no non-full containers found. Cannot store items.");
            return null;
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

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof StoreInStashTask task)
         ? false
         : task.stashRange.equals(this.stashRange)
            && task.getIfNotPresent == this.getIfNotPresent
            && Arrays.equals((Object[])task.toStore, (Object[])this.toStore);
   }

   @Override
   protected String toDebugString() {
      return "Storing in stash " + this.stashRange + ": " + Arrays.toString((Object[])this.toStore);
   }
}
