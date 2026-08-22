package com.player2.playerengine.tasks;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.resources.CollectBucketLiquidTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;
import net.minecraft.world.item.Items;

public class GetRidOfExtraWaterBucketTask extends Task {
   private boolean needsPickup = false;

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      PlayerEngineController mod = this.controller;
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
