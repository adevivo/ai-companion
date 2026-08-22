package com.player2.playerengine.tasks.speedrun.beatgame.prioritytask.tasks;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.speedrun.beatgame.prioritytask.prioritycalculators.ItemPriorityCalculator;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;
import java.util.Arrays;
import java.util.function.Function;

public class ResourcePriorityTask extends PriorityTask {
   private final ItemPriorityCalculator priorityCalculator;
   private final ItemTarget[] collect;
   private boolean collected = false;
   private Task task = null;

   public ResourcePriorityTask(ItemPriorityCalculator priorityCalculator, Function<PlayerEngineController, Boolean> canCall, Task task, ItemTarget... collect) {
      this(priorityCalculator, canCall, false, true, false, collect);
      this.task = task;
   }

   public ResourcePriorityTask(ItemPriorityCalculator priorityCalculator, Function<PlayerEngineController, Boolean> canCall, ItemTarget... collect) {
      this(priorityCalculator, canCall, false, true, false, collect);
   }

   public ResourcePriorityTask(
      ItemPriorityCalculator priorityCalculator,
      Function<PlayerEngineController, Boolean> canCall,
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
   public Task getTask(PlayerEngineController mod) {
      return (Task)(this.task != null ? this.task : TaskCatalogue.getSquashedItemTask(this.collect));
   }

   @Override
   public String getDebugString() {
      return "Collecting resource: " + Arrays.toString((Object[])this.collect);
   }

   @Override
   public double getPriority(PlayerEngineController mod) {
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
