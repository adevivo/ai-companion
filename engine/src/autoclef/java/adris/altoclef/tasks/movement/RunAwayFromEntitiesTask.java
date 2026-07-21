package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.util.baritone.GoalRunAwayFromEntities;
import baritone.api.pathing.goals.Goal;
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
   protected Goal newGoal(AltoClefController mod) {
      return new RunAwayFromEntitiesTask.GoalRunAwayStuff(mod, this.distanceToRun, this.xz);
   }

   private class GoalRunAwayStuff extends GoalRunAwayFromEntities {
      public GoalRunAwayStuff(AltoClefController mod, double distance, boolean xz) {
         super(mod, distance, xz, RunAwayFromEntitiesTask.this.penalty);
      }

      @Override
      protected List<Entity> getEntities(AltoClefController mod) {
         return RunAwayFromEntitiesTask.this.runAwaySupplier.get();
      }
   }
}
