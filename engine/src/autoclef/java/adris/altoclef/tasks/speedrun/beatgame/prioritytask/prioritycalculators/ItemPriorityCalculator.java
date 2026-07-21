package adris.altoclef.tasks.speedrun.beatgame.prioritytask.prioritycalculators;

public abstract class ItemPriorityCalculator {
   public final int minCount;
   public final int maxCount;
   protected boolean minCountSatisfied = false;
   protected boolean maxCountSatisfied = false;

   public ItemPriorityCalculator(int minCount, int maxCount) {
      this.minCount = minCount;
      this.maxCount = maxCount;
   }

   public final double getPriority(int count) {
      if (count > this.minCount) {
         this.minCountSatisfied = true;
      }

      if (count > this.maxCount) {
         this.maxCountSatisfied = true;
      }

      if (this.minCountSatisfied) {
         count = Math.max(this.minCount, count);
      }

      return this.maxCountSatisfied ? Double.NEGATIVE_INFINITY : this.calculatePriority(count);
   }

   abstract double calculatePriority(int var1);
}
