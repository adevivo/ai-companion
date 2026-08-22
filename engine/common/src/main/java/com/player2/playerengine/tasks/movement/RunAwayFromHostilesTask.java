package com.player2.playerengine.tasks.movement;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.baritone.GoalRunAwayFromEntities;
import com.player2.playerengine.util.helpers.BaritoneHelper;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;

public class RunAwayFromHostilesTask extends CustomBaritoneGoalTask {
   private final double distanceToRun;
   private final boolean includeSkeletons;

   public RunAwayFromHostilesTask(double distance, boolean includeSkeletons) {
      this.distanceToRun = distance;
      this.includeSkeletons = includeSkeletons;
   }

   public RunAwayFromHostilesTask(double distance) {
      this(distance, false);
   }

   @Override
   protected Goal newGoal(PlayerEngineController mod) {
      mod.getBaritone().getPathingBehavior().forceCancel();
      return new RunAwayFromHostilesTask.GoalRunAwayFromHostiles(mod, this.distanceToRun);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof RunAwayFromHostilesTask task ? Math.abs(task.distanceToRun - this.distanceToRun) < 1.0 : false;
   }

   @Override
   protected String toDebugString() {
      return "NIGERUNDAYOO, SUMOOKEYY! distance=" + this.distanceToRun + ", skeletons=" + this.includeSkeletons;
   }

   private class GoalRunAwayFromHostiles extends GoalRunAwayFromEntities {
      public GoalRunAwayFromHostiles(PlayerEngineController mod, double distance) {
         super(mod, distance, false, 0.8);
      }

      @Override
      protected List<Entity> getEntities(PlayerEngineController mod) {
         Stream<LivingEntity> stream = mod.getEntityTracker().getHostiles().stream();
         synchronized (BaritoneHelper.MINECRAFT_LOCK) {
            if (!RunAwayFromHostilesTask.this.includeSkeletons) {
               stream = stream.filter(hostile -> !(hostile instanceof Skeleton));
            }

            return stream.collect(Collectors.toList());
         }
      }
   }
}
