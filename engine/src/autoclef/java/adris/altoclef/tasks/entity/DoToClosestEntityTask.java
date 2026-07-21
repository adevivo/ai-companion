package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.AbstractDoToClosestObjectTask;
import adris.altoclef.tasksystem.Task;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class DoToClosestEntityTask extends AbstractDoToClosestObjectTask<Entity> {
   private final Class[] targetEntities;
   private final Supplier<Vec3> getOriginPos;
   private final Function<Entity, Task> getTargetTask;
   private final Predicate<Entity> shouldInteractWith;

   public DoToClosestEntityTask(Supplier<Vec3> getOriginSupplier, Function<Entity, Task> getTargetTask, Predicate<Entity> shouldInteractWith, Class... entities) {
      this.getOriginPos = getOriginSupplier;
      this.getTargetTask = getTargetTask;
      this.shouldInteractWith = shouldInteractWith;
      this.targetEntities = entities;
   }

   public DoToClosestEntityTask(Supplier<Vec3> getOriginSupplier, Function<Entity, Task> getTargetTask, Class... entities) {
      this(getOriginSupplier, getTargetTask, entity -> true, entities);
   }

   public DoToClosestEntityTask(Function<Entity, Task> getTargetTask, Predicate<Entity> shouldInteractWith, Class... entities) {
      this((Supplier<Vec3>)null, getTargetTask, shouldInteractWith, entities);
   }

   public DoToClosestEntityTask(Function<Entity, Task> getTargetTask, Class... entities) {
      this((Supplier<Vec3>)null, getTargetTask, entity -> true, entities);
   }

   protected Vec3 getPos(AltoClefController mod, Entity obj) {
      return obj.position();
   }

   @Override
   protected Optional<Entity> getClosestTo(AltoClefController mod, Vec3 pos) {
      return !mod.getEntityTracker().entityFound(this.targetEntities)
         ? Optional.empty()
         : mod.getEntityTracker().getClosestEntity(pos, this.shouldInteractWith, this.targetEntities);
   }

   @Override
   protected Vec3 getOriginPos(AltoClefController mod) {
      return this.getOriginPos != null ? this.getOriginPos.get() : mod.getPlayer().position();
   }

   protected Task getGoalTask(Entity obj) {
      return this.getTargetTask.apply(obj);
   }

   protected boolean isValid(AltoClefController mod, Entity obj) {
      return obj.isAlive() && mod.getEntityTracker().isEntityReachable(obj) && obj != mod.getEntity();
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof DoToClosestEntityTask task ? Arrays.equals((Object[])task.targetEntities, (Object[])this.targetEntities) : false;
   }

   @Override
   protected String toDebugString() {
      return "Doing something to closest entity...";
   }
}
