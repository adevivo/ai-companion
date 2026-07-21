package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
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
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
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
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
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
