package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasksystem.Task;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalNear;
import net.minecraft.core.BlockPos;

public class GetWithinRangeOfBlockTask extends CustomBaritoneGoalTask {
   public final BlockPos blockPos;
   public final int range;

   public GetWithinRangeOfBlockTask(BlockPos blockPos, int range) {
      this.blockPos = blockPos;
      this.range = range;
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      return new GoalNear(this.blockPos, this.range);
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof GetWithinRangeOfBlockTask task) ? false : task.blockPos.equals(this.blockPos) && task.range == this.range;
   }

   @Override
   protected String toDebugString() {
      return "Getting within " + this.range + " blocks of " + this.blockPos.toShortString();
   }
}
