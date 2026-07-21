package adris.altoclef.tasks.speedrun.beatgame.prioritytask.tasks;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasksystem.Task;
import java.util.function.Function;

public abstract class PriorityTask {
   private final Function<AltoClefController, Boolean> canCall;
   private final boolean shouldForce;
   private final boolean canCache;
   public final boolean bypassForceCooldown;

   public PriorityTask(Function<AltoClefController, Boolean> canCall, boolean shouldForce, boolean canCache, boolean bypassForceCooldown) {
      this.canCall = canCall;
      this.shouldForce = shouldForce;
      this.canCache = canCache;
      this.bypassForceCooldown = bypassForceCooldown;
   }

   public final double calculatePriority(AltoClefController mod) {
      return !this.canCall.apply(mod) ? Double.NEGATIVE_INFINITY : this.getPriority(mod);
   }

   @Override
   public String toString() {
      return this.getDebugString();
   }

   public abstract Task getTask(AltoClefController var1);

   public abstract String getDebugString();

   protected abstract double getPriority(AltoClefController var1);

   public boolean needCraftingOnStart(AltoClefController mod) {
      return false;
   }

   public boolean shouldForce() {
      return this.shouldForce;
   }

   public boolean canCache() {
      return this.canCache;
   }
}
