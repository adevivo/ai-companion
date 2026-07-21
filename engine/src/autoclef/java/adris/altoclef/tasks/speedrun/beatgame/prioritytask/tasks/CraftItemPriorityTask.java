package adris.altoclef.tasks.speedrun.beatgame.prioritytask.tasks;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.tasks.CraftInInventoryTask;
import adris.altoclef.tasks.container.CraftInTableTask;
import adris.altoclef.tasks.speedrun.beatgame.BeatMinecraftTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.RecipeTarget;
import adris.altoclef.util.helpers.CraftingHelper;
import java.util.function.Function;

public class CraftItemPriorityTask extends PriorityTask {
   public final double priority;
   public final RecipeTarget recipeTarget;
   private boolean satisfied = false;

   public CraftItemPriorityTask(double priority, RecipeTarget toCraft) {
      this(priority, toCraft, mod -> true);
   }

   public CraftItemPriorityTask(double priority, RecipeTarget toCraft, Function<AltoClefController, Boolean> canCall) {
      this(priority, toCraft, canCall, false, true, true);
   }

   public CraftItemPriorityTask(double priority, RecipeTarget toCraft, boolean shouldForce, boolean canCache, boolean bypassForceCooldown) {
      this(priority, toCraft, mod -> true, shouldForce, canCache, bypassForceCooldown);
   }

   public CraftItemPriorityTask(
      double priority, RecipeTarget toCraft, Function<AltoClefController, Boolean> canCall, boolean shouldForce, boolean canCache, boolean bypassForceCooldown
   ) {
      super(canCall, shouldForce, canCache, bypassForceCooldown);
      this.priority = priority;
      this.recipeTarget = toCraft;
   }

   @Override
   public Task getTask(AltoClefController mod) {
      return (Task)(this.recipeTarget.getRecipe().isBig() ? new CraftInTableTask(this.recipeTarget) : new CraftInInventoryTask(this.recipeTarget));
   }

   @Override
   public String getDebugString() {
      return "Crafting " + this.recipeTarget;
   }

   @Override
   protected double getPriority(AltoClefController mod) {
      if (BeatMinecraftTask.hasItem(mod, this.recipeTarget.getOutputItem())) {
         Debug.logInternal("THIS IS SATISFIED " + this.recipeTarget.getOutputItem());
         this.satisfied = true;
      }

      Debug.logInternal("NOT SATISFIED");
      return this.satisfied ? Double.NEGATIVE_INFINITY : this.priority;
   }

   @Override
   public boolean needCraftingOnStart(AltoClefController mod) {
      return CraftingHelper.canCraftItemNow(mod, this.recipeTarget.getOutputItem());
   }

   public boolean isSatisfied() {
      return this.satisfied;
   }
}
