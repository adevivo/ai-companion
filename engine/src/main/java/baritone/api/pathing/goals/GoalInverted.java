package baritone.api.pathing.goals;

public class GoalInverted implements Goal {
   public final Goal origin;

   public GoalInverted(Goal origin) {
      this.origin = origin;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      return false;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      return -this.origin.heuristic(x, y, z);
   }

   @Override
   public double heuristic() {
      return Double.NEGATIVE_INFINITY;
   }

   @Override
   public String toString() {
      return String.format("GoalInverted{%s}", this.origin.toString());
   }
}
