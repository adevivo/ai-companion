package baritone.api.pathing.goals;

import baritone.api.BaritoneAPI;

public class GoalAxis implements Goal {
   private static final double SQRT_2_OVER_2 = Math.sqrt(2.0) / 2.0;
   private final int targetHeight;

   public GoalAxis(int targetHeight) {
      this.targetHeight = targetHeight;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      return y == this.targetHeight && (x == 0 || z == 0 || Math.abs(x) == Math.abs(z));
   }

   @Override
   public double heuristic(int x0, int y, int z0) {
      int x = Math.abs(x0);
      int z = Math.abs(z0);
      int shrt = Math.min(x, z);
      int lng = Math.max(x, z);
      int diff = lng - shrt;
      double flatAxisDistance = Math.min((double)x, Math.min((double)z, diff * SQRT_2_OVER_2));
      return flatAxisDistance * BaritoneAPI.getGlobalSettings().costHeuristic.get() + GoalYLevel.calculate(BaritoneAPI.getGlobalSettings().axisHeight.get(), y);
   }

   @Override
   public String toString() {
      return "GoalAxis";
   }
}
