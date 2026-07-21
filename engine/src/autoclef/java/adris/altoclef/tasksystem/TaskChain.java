package adris.altoclef.tasksystem;

import adris.altoclef.AltoClefController;
import java.util.ArrayList;
import java.util.List;

public abstract class TaskChain {
   protected AltoClefController controller;
   private final List<Task> cachedTaskChain = new ArrayList<>();

   public TaskChain(TaskRunner runner) {
      runner.addTaskChain(this);
      this.controller = runner.getMod();
   }

   public void tick() {
      this.cachedTaskChain.clear();
      this.onTick();
   }

   public void stop() {
      this.cachedTaskChain.clear();
      this.onStop();
   }

   protected abstract void onStop();

   public abstract void onInterrupt(TaskChain var1);

   protected abstract void onTick();

   public abstract float getPriority();

   public abstract boolean isActive();

   public abstract String getName();

   public List<Task> getTasks() {
      return this.cachedTaskChain;
   }

   void addTaskToChain(Task task) {
      this.cachedTaskChain.add(task);
   }

   @Override
   public String toString() {
      return this.getName();
   }
}
