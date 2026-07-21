package adris.altoclef.tasks.speedrun.beatgame.prioritytask.tasks;

import adris.altoclef.AltoClefController;
import adris.altoclef.util.RecipeTarget;
import java.util.function.Function;

public class RecraftableItemPriorityTask extends CraftItemPriorityTask {
   private final double recraftPriority;

   public RecraftableItemPriorityTask(double priority, double recraftPriority, RecipeTarget toCraft, Function<AltoClefController, Boolean> canCall) {
      super(priority, toCraft, canCall);
      this.recraftPriority = recraftPriority;
   }

   @Override
   protected double getPriority(AltoClefController mod) {
      return this.isSatisfied() ? this.recraftPriority : super.getPriority(mod);
   }
}
