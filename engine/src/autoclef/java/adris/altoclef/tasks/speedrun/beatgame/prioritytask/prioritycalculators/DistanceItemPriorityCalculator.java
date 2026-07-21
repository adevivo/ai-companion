package adris.altoclef.tasks.speedrun.beatgame.prioritytask.prioritycalculators;

public class DistanceItemPriorityCalculator extends DistancePriorityCalculator {
   private final double multiplier;
   private final double unneededMultiplier;
   private final double unneededDistanceThreshold;

   public DistanceItemPriorityCalculator(double multiplier, double unneededMultiplier, double unneededDistanceThreshold, int minCount, int maxCount) {
      super(minCount, maxCount);
      this.multiplier = multiplier;
      this.unneededMultiplier = unneededMultiplier;
      this.unneededDistanceThreshold = unneededDistanceThreshold;
   }

   @Override
   protected double calculatePriority(double distance) {
      double priority = 1.0 / distance;
      if (this.minCountSatisfied) {
         return distance < this.unneededDistanceThreshold ? priority * this.unneededMultiplier : Double.NEGATIVE_INFINITY;
      } else {
         return priority * this.multiplier;
      }
   }
}
