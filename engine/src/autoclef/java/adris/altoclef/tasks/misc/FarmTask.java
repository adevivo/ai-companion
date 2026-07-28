package adris.altoclef.tasks.misc;

import adris.altoclef.Debug;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.time.TimerGame;
import baritone.api.process.IFarmProcess;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public class FarmTask extends Task {
   private final Integer range;
   private final BlockPos center;
   /**
    * Rate limit for the restart in {@link #onTick}. Without it a farm process that reports itself
    * inactive gets re-armed twenty times a second, which is fast enough to keep clearing its own block
    * scan before it can ever be used — the companion then stands still indefinitely while this task
    * still reports {@code <Farming ...>}.
    */
   private final TimerGame restartTimer = new TimerGame(1.0);
   /** The last stall reason relayed to the agent, so an unchanged one is not repeated every tick. */
   private String reportedStall;

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
         if (this.restartTimer.elapsed()) {
            this.restartTimer.reset();
            // Worth saying out loud: a farm that keeps needing to be restarted is not farming, and this
            // used to be entirely invisible.
            Debug.logMessage("Farm process went inactive; restarting it.");
            this.onStart();
         }
      } else {
         this.restartTimer.reset();
      }

      // The task status alone says "<Farming ...>" for as long as this task is armed, which is how the
      // companion came to narrate a harvest that was not happening. Push the process's own verdict into
      // the agent's next turn so it can say what is actually going on.
      String stall = farmProcess.getStallReason();
      if (!Objects.equals(stall, this.reportedStall)) {
         this.reportedStall = stall;
         if (stall != null) {
            this.controller.logAgentInfo("Farm status: " + stall + ".");
         }
      }

      this.setDebugState(stall == null ? "Farming with Automatone..." : "Farming (" + stall + ")");
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
