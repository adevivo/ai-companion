package com.player2.playerengine.tasks.movement;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.Dimension;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalYLevel;

public class GetToYTask extends CustomBaritoneGoalTask {
   private final int yLevel;
   private final Dimension dimension;

   public GetToYTask(int ylevel, Dimension dimension) {
      this.yLevel = ylevel;
      this.dimension = dimension;
   }

   public GetToYTask(int ylevel) {
      this(ylevel, null);
   }

   @Override
   protected Task onTick() {
      return (Task)(this.dimension != null && WorldHelper.getCurrentDimension(this.controller) != this.dimension
         ? new DefaultGoToDimensionTask(this.dimension)
         : super.onTick());
   }

   @Override
   protected Goal newGoal(PlayerEngineController mod) {
      return new GoalYLevel(this.yLevel);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof GetToYTask task ? task.yLevel == this.yLevel : false;
   }

   @Override
   protected String toDebugString() {
      return "Going to y=" + this.yLevel + (this.dimension != null ? "in dimension" + this.dimension : "");
   }
}
