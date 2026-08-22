package com.player2.playerengine.tasks.speedrun.beatgame.prioritytask.tasks;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.RecipeTarget;
import java.util.function.Function;

public class RecraftableItemPriorityTask extends CraftItemPriorityTask {
   private final double recraftPriority;

   public RecraftableItemPriorityTask(double priority, double recraftPriority, RecipeTarget toCraft, Function<PlayerEngineController, Boolean> canCall) {
      super(priority, toCraft, canCall);
      this.recraftPriority = recraftPriority;
   }

   @Override
   protected double getPriority(PlayerEngineController mod) {
      return this.isSatisfied() ? this.recraftPriority : super.getPriority(mod);
   }
}
