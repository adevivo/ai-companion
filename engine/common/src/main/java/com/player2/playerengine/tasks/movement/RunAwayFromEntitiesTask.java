package com.player2.playerengine.tasks.movement;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.baritone.GoalRunAwayFromEntities;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;

public abstract class RunAwayFromEntitiesTask extends CustomBaritoneGoalTask {
   private final Supplier<List<Entity>> runAwaySupplier;
   private final double distanceToRun;
   private final boolean xz;
   private final double penalty;

   public RunAwayFromEntitiesTask(Supplier<List<Entity>> toRunAwayFrom, double distanceToRun, boolean xz, double penalty) {
      this.runAwaySupplier = toRunAwayFrom;
      this.distanceToRun = distanceToRun;
      this.xz = xz;
      this.penalty = penalty;
   }

   public RunAwayFromEntitiesTask(Supplier<List<Entity>> toRunAwayFrom, double distanceToRun, double penalty) {
      this(toRunAwayFrom, distanceToRun, false, penalty);
   }

   @Override
   protected Goal newGoal(PlayerEngineController mod) {
      return new RunAwayFromEntitiesTask.GoalRunAwayStuff(mod, this.distanceToRun, this.xz);
   }

   private class GoalRunAwayStuff extends GoalRunAwayFromEntities {
      public GoalRunAwayStuff(PlayerEngineController mod, double distance, boolean xz) {
         super(mod, distance, xz, RunAwayFromEntitiesTask.this.penalty);
      }

      @Override
      protected List<Entity> getEntities(PlayerEngineController mod) {
         return RunAwayFromEntitiesTask.this.runAwaySupplier.get();
      }
   }
}
