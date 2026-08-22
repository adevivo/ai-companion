package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.entity.KillEntitiesTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;

public class KillAndLootTask extends ResourceTask {
   private final Class<?> toKill;
   private final Task killTask;

   public KillAndLootTask(Class<?> toKill, Predicate<Entity> shouldKill, ItemTarget... itemTargets) {
      super((ItemTarget[])itemTargets.clone());
      this.toKill = toKill;
      this.killTask = new KillEntitiesTask(shouldKill, this.toKill);
   }

   public KillAndLootTask(Class<?> toKill, ItemTarget... itemTargets) {
      super((ItemTarget[])itemTargets.clone());
      this.toKill = toKill;
      this.killTask = new KillEntitiesTask(this.toKill);
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController mod) {
   }

   @Override
   protected Task onResourceTick(PlayerEngineController mod) {
      if (!mod.getEntityTracker().entityFound(this.toKill)) {
         if (this.isInWrongDimension(mod)) {
            this.setDebugState("Going to correct dimension.");
            return this.getToCorrectDimensionTask(mod);
         } else {
            this.setDebugState("Searching for mob...");
            return new TimeoutWanderTask();
         }
      } else {
         return this.killTask;
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof KillAndLootTask task ? task.toKill.equals(this.toKill) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Collect items from " + this.toKill.toGenericString();
   }
}
