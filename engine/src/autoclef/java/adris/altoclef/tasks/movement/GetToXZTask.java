package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalXZ;
import net.minecraft.core.BlockPos;

public class GetToXZTask extends CustomBaritoneGoalTask {
   private final int x;
   private final int z;
   private final Dimension dimension;

   public GetToXZTask(int x, int z) {
      this(x, z, null);
   }

   public GetToXZTask(int x, int z, Dimension dimension) {
      this.x = x;
      this.z = z;
      this.dimension = dimension;
   }

   @Override
   protected Task onTick() {
      return (Task)(this.dimension != null && WorldHelper.getCurrentDimension(this.controller) != this.dimension
         ? new DefaultGoToDimensionTask(this.dimension)
         : super.onTick());
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      return new GoalXZ(this.x, this.z);
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof GetToXZTask task) ? false : task.x == this.x && task.z == this.z && task.dimension == this.dimension;
   }

   @Override
   public boolean isFinished() {
      BlockPos cur = this.controller.getPlayer().blockPosition();
      return cur.getX() == this.x && cur.getZ() == this.z && (this.dimension == null || this.dimension == WorldHelper.getCurrentDimension(this.controller));
   }

   @Override
   protected String toDebugString() {
      return "Getting to (" + this.x + "," + this.z + ")" + (this.dimension != null ? " in dimension " + this.dimension : "");
   }
}
