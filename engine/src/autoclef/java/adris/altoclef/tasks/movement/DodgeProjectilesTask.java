package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.baritone.GoalDodgeProjectiles;
import baritone.api.pathing.goals.Goal;

public class DodgeProjectilesTask extends CustomBaritoneGoalTask {
   private final double distanceHorizontal;
   private final double distanceVertical;

   public DodgeProjectilesTask(double distanceHorizontal, double distanceVertical) {
      this.distanceHorizontal = distanceHorizontal;
      this.distanceVertical = distanceVertical;
   }

   @Override
   protected Task onTick() {
      if (this.cachedGoal != null) {
         GoalDodgeProjectiles var1 = (GoalDodgeProjectiles)this.cachedGoal;
      }

      return super.onTick();
   }

   @Override
   protected boolean isEqual(Task other) {
      if (other instanceof DodgeProjectilesTask task) {
         return Math.abs(task.distanceHorizontal - this.distanceHorizontal) > 1.0 ? false : !(Math.abs(task.distanceVertical - this.distanceVertical) > 1.0);
      } else {
         return false;
      }
   }

   @Override
   protected String toDebugString() {
      return "Dodge arrows at " + this.distanceHorizontal + " blocks away";
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      return new GoalDodgeProjectiles(mod, this.distanceHorizontal, this.distanceVertical);
   }
}
