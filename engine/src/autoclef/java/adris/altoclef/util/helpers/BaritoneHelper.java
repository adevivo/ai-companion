package adris.altoclef.util.helpers;

import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.world.phys.Vec3;

public class BaritoneHelper {
   public static final Object MINECRAFT_LOCK = new Object();

   public static double calculateGenericHeuristic(Vec3 start, Vec3 target) {
      return calculateGenericHeuristic(start.x, start.y, start.z, target.x, target.y, target.z);
   }

   public static double calculateGenericHeuristic(double xStart, double yStart, double zStart, double xTarget, double yTarget, double zTarget) {
      double xDiff = xTarget - xStart;
      int yDiff = (int)yTarget - (int)yStart;
      double zDiff = zTarget - zStart;
      return GoalBlock.calculate(xDiff, yDiff < 0 ? yDiff - 1 : yDiff, zDiff);
   }
}
