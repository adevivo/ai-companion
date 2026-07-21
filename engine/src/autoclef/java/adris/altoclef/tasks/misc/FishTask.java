package adris.altoclef.tasks.misc;

import adris.altoclef.tasksystem.Task;
import baritone.Baritone;
import baritone.process.FishingProcess;
import net.minecraft.world.item.Items;

public class FishTask extends Task {
   @Override
   protected void onStart() {
      ((Baritone)this.controller.getBaritone()).getFishingProcess().fish();
   }

   @Override
   protected Task onTick() {
      FishingProcess fishingProcess = ((Baritone)this.controller.getBaritone()).getFishingProcess();
      if (!this.controller.getSlotHandler().forceEquipItem(Items.FISHING_ROD)) {
         this.setDebugState("Can't fish without a fishing rod");
         return null;
      } else {
         if (!fishingProcess.isActive()) {
            this.onStart();
         }

         this.setDebugState("Fishing with Automatone...");
         return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      FishingProcess fishingProcess = ((Baritone)this.controller.getBaritone()).getFishingProcess();
      if (fishingProcess.isActive()) {
         fishingProcess.onLostControl();
      }
   }

   @Override
   public boolean isFinished() {
      return false;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof FishTask;
   }

   @Override
   protected String toDebugString() {
      return "Fishing";
   }
}
