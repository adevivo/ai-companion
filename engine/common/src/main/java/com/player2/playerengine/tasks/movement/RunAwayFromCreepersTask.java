package com.player2.playerengine.tasks.movement;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.chains.MobDefenseChain;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.baritone.GoalRunAwayFromEntities;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

public class RunAwayFromCreepersTask extends CustomBaritoneGoalTask {
   private final double distanceToRun;

   public RunAwayFromCreepersTask(double distance) {
      this.distanceToRun = distance;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof RunAwayFromCreepersTask task ? !(Math.abs(task.distanceToRun - this.distanceToRun) > 1.0) : false;
   }

   @Override
   protected String toDebugString() {
      return "Run " + this.distanceToRun + " blocks away from creepers";
   }

   @Override
   protected Goal newGoal(PlayerEngineController mod) {
      mod.getBaritone().getPathingBehavior().forceCancel();
      return new RunAwayFromCreepersTask.GoalRunAwayFromCreepers(mod, this.distanceToRun);
   }

   private static class GoalRunAwayFromCreepers extends GoalRunAwayFromEntities {
      public GoalRunAwayFromCreepers(PlayerEngineController mod, double distance) {
         super(mod, distance, false, 10.0);
      }

      @Override
      protected List<Entity> getEntities(PlayerEngineController mod) {
         return new ArrayList<>(mod.getEntityTracker().getTrackedEntities(Creeper.class));
      }

      @Override
      protected double getCostOfEntity(Entity entity, int x, int y, int z) {
         return entity instanceof Creeper
            ? MobDefenseChain.getCreeperSafety(new Vec3(x + 0.5, y + 0.5, z + 0.5), (Creeper)entity)
            : super.getCostOfEntity(entity, x, y, z);
      }
   }
}
