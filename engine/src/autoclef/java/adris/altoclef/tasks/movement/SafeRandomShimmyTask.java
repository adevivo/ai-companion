package adris.altoclef.tasks.movement;

import adris.altoclef.Debug;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.IBaritone;
import baritone.api.utils.input.Input;

public class SafeRandomShimmyTask extends Task {
   private final TimerGame lookTimer;

   public SafeRandomShimmyTask(float randomLookInterval) {
      this.lookTimer = new TimerGame(randomLookInterval);
   }

   public SafeRandomShimmyTask() {
      this(5.0F);
   }

   @Override
   protected void onStart() {
      this.lookTimer.reset();
   }

   @Override
   protected Task onTick() {
      if (this.lookTimer.elapsed()) {
         Debug.logMessage("Random Orientation");
         this.lookTimer.reset();
         LookHelper.randomOrientation(this.controller);
      }

      IBaritone baritone = this.controller.getBaritone();
      baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
      baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
      baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
      return null;
   }

   @Override
   protected void onStop(Task interruptTask) {
      IBaritone baritone = this.controller.getBaritone();
      baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
      baritone.getInputOverrideHandler().setInputForceState(Input.SNEAK, false);
      baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof SafeRandomShimmyTask;
   }

   @Override
   protected String toDebugString() {
      return "Shimmying";
   }
}
