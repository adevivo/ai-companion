package adris.altoclef.tasks.misc;

import adris.altoclef.tasksystem.Task;
import baritone.api.process.IFarmProcess;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public class FarmTask extends Task {
   private final Integer range;
   private final BlockPos center;

   public FarmTask(Integer range, BlockPos center) {
      this.range = range;
      this.center = center;
   }

   public FarmTask() {
      this(null, null);
   }

   @Override
   protected void onStart() {
      IFarmProcess farmProcess = this.controller.getBaritone().getFarmProcess();
      if (this.range != null && this.center != null) {
         farmProcess.farm(this.range, this.center);
      } else if (this.range != null) {
         farmProcess.farm(this.range);
      } else {
         farmProcess.farm();
      }
   }

   @Override
   protected Task onTick() {
      IFarmProcess farmProcess = this.controller.getBaritone().getFarmProcess();
      if (!farmProcess.isActive()) {
         this.onStart();
      }

      this.setDebugState("Farming with Automatone...");
      return null;
   }

   @Override
   protected void onStop(Task interruptTask) {
      IFarmProcess farmProcess = this.controller.getBaritone().getFarmProcess();
      if (farmProcess.isActive()) {
         farmProcess.onLostControl();
      }
   }

   @Override
   public boolean isFinished() {
      return false;
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof FarmTask task) ? false : Objects.equals(task.range, this.range) && Objects.equals(task.center, this.center);
   }

   @Override
   protected String toDebugString() {
      return this.range != null && this.center != null ? "Farming in range " + this.range + " around " + this.center.toShortString() : "Farming nearby";
   }
}
