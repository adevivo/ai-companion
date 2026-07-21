package adris.altoclef.tasks.speedrun.beatgame.prioritytask.tasks;

import adris.altoclef.AltoClefController;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.speedrun.beatgame.prioritytask.prioritycalculators.ItemPriorityCalculator;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import java.util.Arrays;
import java.util.function.Function;

public class ResourcePriorityTask extends PriorityTask {
   private final ItemPriorityCalculator priorityCalculator;
   private final ItemTarget[] collect;
   private boolean collected = false;
   private Task task = null;

   public ResourcePriorityTask(ItemPriorityCalculator priorityCalculator, Function<AltoClefController, Boolean> canCall, Task task, ItemTarget... collect) {
      this(priorityCalculator, canCall, false, true, false, collect);
      this.task = task;
   }

   public ResourcePriorityTask(ItemPriorityCalculator priorityCalculator, Function<AltoClefController, Boolean> canCall, ItemTarget... collect) {
      this(priorityCalculator, canCall, false, true, false, collect);
   }

   public ResourcePriorityTask(
      ItemPriorityCalculator priorityCalculator,
      Function<AltoClefController, Boolean> canCall,
      boolean shouldForce,
      boolean canCache,
      boolean bypassForceCooldown,
      ItemTarget... collect
   ) {
      super(canCall, shouldForce, canCache, bypassForceCooldown);
      this.collect = collect;
      this.priorityCalculator = priorityCalculator;
   }

   @Override
   public Task getTask(AltoClefController mod) {
      return (Task)(this.task != null ? this.task : TaskCatalogue.getSquashedItemTask(this.collect));
   }

   @Override
   public String getDebugString() {
      return "Collecting resource: " + Arrays.toString((Object[])this.collect);
   }

   @Override
   public double getPriority(AltoClefController mod) {
      if (this.collected) {
         return Double.NEGATIVE_INFINITY;
      } else {
         int count = 0;

         for (ItemTarget target : this.collect) {
            count += mod.getItemStorage().getItemCount(target.getMatches());
         }

         if (count >= this.priorityCalculator.maxCount) {
            this.collected = true;
         }

         return this.priorityCalculator.getPriority(count);
      }
   }

   public boolean isCollected() {
      return this.collected;
   }
}
