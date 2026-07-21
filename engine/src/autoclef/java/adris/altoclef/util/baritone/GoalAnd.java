package adris.altoclef.util.baritone;

import baritone.api.pathing.goals.Goal;
import java.util.Arrays;

public class GoalAnd implements Goal {
   private final Goal[] goals;

   public GoalAnd(Goal... goals) {
      this.goals = goals;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      Goal[] var4 = this.goals;
      int var5 = var4.length;

      for (Goal goal : var4) {
         if (!goal.isInGoal(x, y, z)) {
            return false;
         }
      }

      return true;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      double sum = 0.0;
      if (this.goals != null) {
         for (Goal goal : this.goals) {
            sum += goal.heuristic(x, y, z);
         }
      }

      return sum;
   }

   @Override
   public String toString() {
      return "GoalAnd" + Arrays.toString((Object[])this.goals);
   }

   public Goal[] goals() {
      return this.goals;
   }
}
