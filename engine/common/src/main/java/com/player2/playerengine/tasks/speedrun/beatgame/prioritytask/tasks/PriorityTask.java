package com.player2.playerengine.tasks.speedrun.beatgame.prioritytask.tasks;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.base.Task;
import java.util.function.Function;

public abstract class PriorityTask {
   private final Function<PlayerEngineController, Boolean> canCall;
   private final boolean shouldForce;
   private final boolean canCache;
   public final boolean bypassForceCooldown;

   public PriorityTask(Function<PlayerEngineController, Boolean> canCall, boolean shouldForce, boolean canCache, boolean bypassForceCooldown) {
      this.canCall = canCall;
      this.shouldForce = shouldForce;
      this.canCache = canCache;
      this.bypassForceCooldown = bypassForceCooldown;
   }

   public final double calculatePriority(PlayerEngineController mod) {
      return !this.canCall.apply(mod) ? Double.NEGATIVE_INFINITY : this.getPriority(mod);
   }

   @Override
   public String toString() {
      return this.getDebugString();
   }

   public abstract Task getTask(PlayerEngineController var1);

   public abstract String getDebugString();

   protected abstract double getPriority(PlayerEngineController var1);

   public boolean needCraftingOnStart(PlayerEngineController mod) {
      return false;
   }

   public boolean shouldForce() {
      return this.shouldForce;
   }

   public boolean canCache() {
      return this.canCache;
   }
}
