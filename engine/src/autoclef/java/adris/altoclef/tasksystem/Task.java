package adris.altoclef.tasksystem;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import java.util.function.Predicate;

public abstract class Task {
   public AltoClefController controller;
   private String oldDebugState = "";
   private String debugState = "";
   private Task sub = null;
   private boolean first = true;
   private boolean stopped = false;
   private boolean active = false;

   public void tick(TaskChain parentChain) {
      this.controller = parentChain.controller;
      parentChain.addTaskToChain(this);
      if (this.first) {
         Debug.logInternal("Task START: " + this);
         this.active = true;
         this.onStart();
         this.first = false;
         this.stopped = false;
      }

      if (!this.stopped) {
         Task newSub = this.onTick();
         if (!this.oldDebugState.equals(this.debugState)) {
            Debug.logInternal(this.toString());
            this.oldDebugState = this.debugState;
         }

         if (newSub != null) {
            if (!newSub.isEqual(this.sub) && this.canBeInterrupted(this.sub, newSub)) {
               if (this.sub != null) {
                  this.sub.stop(newSub);
               }

               this.sub = newSub;
            }

            this.sub.tick(parentChain);
         } else if (this.sub != null && this.canBeInterrupted(this.sub, null)) {
            this.sub.stop();
            this.sub = null;
         }
      }
   }

   public void reset() {
      this.first = true;
      this.active = false;
      this.stopped = false;
   }

   public void stop() {
      this.stop(null);
   }

   public void stop(Task interruptTask) {
      if (this.active) {
         Debug.logInternal("Task STOP: " + this + ", interrupted by " + interruptTask);
         if (!this.first) {
            this.onStop(interruptTask);
         }

         if (this.sub != null && !this.sub.stopped()) {
            this.sub.stop(interruptTask);
         }

         this.first = true;
         this.active = false;
         this.stopped = true;
      }
   }

   public void fail(String reason) {
      this.stop();
      Debug.logMessage("Task FAILED: " + reason);
   }

   public void interrupt(Task interruptTask) {
      if (this.active) {
         if (!this.first) {
            this.onStop(interruptTask);
         }

         if (this.sub != null && !this.sub.stopped()) {
            this.sub.interrupt(interruptTask);
         }

         this.first = true;
      }
   }

   protected void setDebugState(String state) {
      if (state == null) {
         state = "";
      }

      this.debugState = state;
   }

   public boolean isFinished() {
      return false;
   }

   public boolean isActive() {
      return this.active;
   }

   public boolean stopped() {
      return this.stopped;
   }

   protected abstract void onStart();

   protected abstract Task onTick();

   protected abstract void onStop(Task var1);

   protected abstract boolean isEqual(Task var1);

   protected abstract String toDebugString();

   @Override
   public String toString() {
      return "<" + this.toDebugString() + "> " + this.debugState;
   }

   @Override
   public boolean equals(Object obj) {
      return obj instanceof Task task ? this.isEqual(task) : false;
   }

   public boolean thisOrChildSatisfies(Predicate<Task> pred) {
      for (Task t = this; t != null; t = t.sub) {
         if (pred.test(t)) {
            return true;
         }
      }

      return false;
   }

   public boolean thisOrChildAreTimedOut() {
      return this.thisOrChildSatisfies(task -> task instanceof TimeoutWanderTask);
   }

   private boolean canBeInterrupted(Task subTask, Task toInterruptWith) {
      return subTask == null ? true : subTask.thisOrChildSatisfies(task -> {
         if (task instanceof ITaskCanForce canForce) {
            if (toInterruptWith != null && toInterruptWith.controller == null) {
               toInterruptWith.controller = this.controller;
            }

            return !canForce.shouldForce(toInterruptWith);
         } else {
            return true;
         }
      });
   }

   public String getTaskTree() {
      StringBuilder builder = new StringBuilder("Main task:\n");
      Task cur = this;

      while (cur != null) {
         builder.append(cur.toDebugString());
         cur = cur.sub;
         if (cur != null) {
            builder.append("\nFor that doing:\n");
         }
      }

      return builder.toString();
   }
}
