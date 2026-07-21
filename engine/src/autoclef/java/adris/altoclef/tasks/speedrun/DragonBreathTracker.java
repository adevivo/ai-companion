package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClefController;
import adris.altoclef.BotBehaviour;
import adris.altoclef.tasks.movement.CustomBaritoneGoalTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalRunAway;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.AreaEffectCloud;

public class DragonBreathTracker {
   private final HashSet<BlockPos> breathBlocks = new HashSet<>();

   public void updateBreath(AltoClefController mod) {
      this.breathBlocks.clear();

      for (AreaEffectCloud cloud : mod.getEntityTracker().getTrackedEntities(AreaEffectCloud.class)) {
         for (BlockPos bad : WorldHelper.getBlocksTouchingBox(cloud.getBoundingBox())) {
            this.breathBlocks.add(bad);
         }
      }
   }

   public boolean isTouchingDragonBreath(BlockPos pos) {
      return this.breathBlocks.contains(pos);
   }

   public Task getRunAwayTask() {
      return new DragonBreathTracker.RunAwayFromDragonsBreathTask();
   }

   private class RunAwayFromDragonsBreathTask extends CustomBaritoneGoalTask {
      @Override
      protected void onStart() {
         super.onStart();
         BotBehaviour botBehaviour = this.controller.getBehaviour();
         botBehaviour.push();
         botBehaviour.setBlockPlacePenalty(Double.POSITIVE_INFINITY);
         this.checker = new MovementProgressChecker(Integer.MAX_VALUE);
      }

      @Override
      protected void onStop(Task interruptTask) {
         super.onStop(interruptTask);
         this.controller.getBehaviour().pop();
      }

      @Override
      protected Goal newGoal(AltoClefController mod) {
         return new GoalRunAway(10.0, DragonBreathTracker.this.breathBlocks.toArray(BlockPos[]::new));
      }

      @Override
      protected boolean isEqual(Task other) {
         return other instanceof DragonBreathTracker.RunAwayFromDragonsBreathTask;
      }

      @Override
      protected String toDebugString() {
         return "ESCAPE Dragons Breath";
      }
   }
}
