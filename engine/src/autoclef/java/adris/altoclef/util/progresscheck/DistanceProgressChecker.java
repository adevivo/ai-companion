package adris.altoclef.util.progresscheck;

import net.minecraft.world.phys.Vec3;

public class DistanceProgressChecker implements IProgressChecker<Vec3> {
   private final IProgressChecker<Double> distanceChecker;
   private final boolean reduceDistance;
   private Vec3 start;
   private Vec3 prevPos;

   public DistanceProgressChecker(IProgressChecker<Double> distanceChecker, boolean reduceDistance) {
      this.distanceChecker = distanceChecker;
      this.reduceDistance = reduceDistance;
      if (reduceDistance) {
         this.distanceChecker.setProgress(Double.NEGATIVE_INFINITY);
      }

      this.reset();
   }

   public DistanceProgressChecker(double timeout, double minDistanceToMake, boolean reduceDistance) {
      this(new LinearProgressChecker(timeout, minDistanceToMake), reduceDistance);
   }

   public DistanceProgressChecker(double timeout, double minDistanceToMake) {
      this(timeout, minDistanceToMake, false);
   }

   public void setProgress(Vec3 position) {
      if (this.start == null) {
         this.start = position;
      } else {
         double delta = position.distanceTo(this.start);
         if (this.reduceDistance) {
            delta *= -1.0;
         }

         this.prevPos = position;
         this.distanceChecker.setProgress(delta);
      }
   }

   @Override
   public boolean failed() {
      return this.distanceChecker.failed();
   }

   @Override
   public void reset() {
      this.start = null;
      this.distanceChecker.setProgress(0.0);
      this.distanceChecker.reset();
   }
}
