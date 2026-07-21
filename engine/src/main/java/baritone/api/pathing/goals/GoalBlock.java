package baritone.api.pathing.goals;

import baritone.api.utils.SettingsUtil;
import baritone.api.utils.interfaces.IGoalRenderPos;
import net.minecraft.core.BlockPos;

public class GoalBlock implements Goal, IGoalRenderPos {
   public final int x;
   public final int y;
   public final int z;

   public GoalBlock(BlockPos pos) {
      this(pos.getX(), pos.getY(), pos.getZ());
   }

   public GoalBlock(int x, int y, int z) {
      this.x = x;
      this.y = y;
      this.z = z;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      return x == this.x && y == this.y && z == this.z;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      int xDiff = x - this.x;
      int yDiff = y - this.y;
      int zDiff = z - this.z;
      return calculate(xDiff, yDiff, zDiff);
   }

   @Override
   public String toString() {
      return String.format("GoalBlock{x=%s,y=%s,z=%s}", SettingsUtil.maybeCensor(this.x), SettingsUtil.maybeCensor(this.y), SettingsUtil.maybeCensor(this.z));
   }

   @Override
   public BlockPos getGoalPos() {
      return new BlockPos(this.x, this.y, this.z);
   }

   public static double calculate(double xDiff, int yDiff, double zDiff) {
      double heuristic = 0.0;
      heuristic += GoalYLevel.calculate(0, yDiff);
      return heuristic + GoalXZ.calculate(xDiff, zDiff);
   }
}
