package adris.altoclef.tasks;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.resources.CollectBucketLiquidTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.world.item.Items;

public class GetRidOfExtraWaterBucketTask extends Task {
   private boolean needsPickup = false;

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      if (mod.getItemStorage().getItemCount(Items.WATER_BUCKET) != 0 && !this.needsPickup) {
         return new InteractWithBlockTask(new ItemTarget(Items.WATER_BUCKET, 1), mod.getPlayer().blockPosition().below(), false);
      } else {
         this.needsPickup = true;
         return mod.getItemStorage().getItemCount(Items.WATER_BUCKET) < 1 ? new CollectBucketLiquidTask.CollectWaterBucketTask(1) : null;
      }
   }

   @Override
   public boolean isFinished() {
      return this.controller.getItemStorage().getItemCount(Items.WATER_BUCKET) == 1 && this.needsPickup;
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof GetRidOfExtraWaterBucketTask;
   }

   @Override
   protected String toDebugString() {
      return null;
   }
}
