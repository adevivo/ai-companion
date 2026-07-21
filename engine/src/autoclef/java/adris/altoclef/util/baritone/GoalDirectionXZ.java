package adris.altoclef.util.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.Goal;
import net.minecraft.world.phys.Vec3;

public class GoalDirectionXZ implements Goal {
   private final double originx;
   private final double originz;
   private final double dirx;
   private final double dirz;
   private final double sidePenalty;

   public GoalDirectionXZ(Vec3 origin, Vec3 offset, double sidePenalty) {
      this.originx = origin.x();
      this.originz = origin.z();
      offset = offset.multiply(1.0, 0.0, 1.0);
      offset = offset.normalize();
      this.dirx = offset.x;
      this.dirz = offset.z;
      if (this.dirx == 0.0 && this.dirz == 0.0) {
         throw new IllegalArgumentException(String.valueOf(offset));
      } else {
         this.sidePenalty = sidePenalty;
      }
   }

   private static String maybeCensor(double value) {
      return BaritoneAPI.getGlobalSettings().censorCoordinates.get() ? "<censored>" : Double.toString(value);
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      return false;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      double dx = x - this.originx;
      double dz = z - this.originz;
      double correctDistance = dx * this.dirx + dz * this.dirz;
      double px = this.dirx * correctDistance;
      double pz = this.dirz * correctDistance;
      double perpendicularDistance = (dx - px) * (dx - px) + (dz - pz) * (dz - pz);
      return -correctDistance * BaritoneAPI.getGlobalSettings().costHeuristic.get() + perpendicularDistance * this.sidePenalty;
   }

   @Override
   public String toString() {
      return String.format(
         "GoalDirection{x=%s, z=%s, dx=%s, dz=%s}", maybeCensor(this.originx), maybeCensor(this.originz), maybeCensor(this.dirx), maybeCensor(this.dirz)
      );
   }
}
