package com.player2.playerengine.tasks.squashed;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.container.CraftInTableTask;
import com.player2.playerengine.tasks.container.UpgradeInSmithingTableTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.StorageHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ArrayUtils;

public class CataloguedResourceTask extends ResourceTask {
   private final CataloguedResourceTask.TaskSquasher squasher = new CataloguedResourceTask.TaskSquasher();
   private final ItemTarget[] targets;
   private final List<ResourceTask> tasksToComplete;

   public CataloguedResourceTask(boolean squash, ItemTarget... targets) {
      super(targets);
      this.targets = targets;
      this.tasksToComplete = new ArrayList<>(targets.length);

      for (ItemTarget target : targets) {
         if (target != null) {
            this.tasksToComplete.add(TaskCatalogue.getItemTask(target));
         }
      }

      if (squash) {
         this.squashTasks(this.tasksToComplete);
      }
   }

   public CataloguedResourceTask(ItemTarget... targets) {
      this(true, targets);
   }

   @Override
   protected void onResourceStart(PlayerEngineController mod) {
   }

   @Override
   protected Task onResourceTick(PlayerEngineController mod) {
      for (ResourceTask task : this.tasksToComplete) {
         for (ItemTarget target : task.getItemTargets()) {
            if (!StorageHelper.itemTargetsMetInventory(mod, target)) {
               return task;
            }
         }
      }

      return null;
   }

   @Override
   public boolean isFinished() {
      for (ResourceTask task : this.tasksToComplete) {
         for (ItemTarget target : task.getItemTargets()) {
            if (!StorageHelper.itemTargetsMetInventory(this.controller, target)) {
               return false;
            }
         }
      }

      return true;
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CataloguedResourceTask task ? Arrays.equals((Object[])task.targets, (Object[])this.targets) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Get catalogued: " + ArrayUtils.toString(this.targets);
   }

   private void squashTasks(List<ResourceTask> tasks) {
      this.squasher.addTasks(tasks);
      tasks.clear();
      tasks.addAll(this.squasher.getSquashed());
   }

   static class TaskSquasher {
      private final Map<Class, TypeSquasher> squashMap = new HashMap<>();
      private final List<ResourceTask> unSquashableTasks = new ArrayList<>();

      public TaskSquasher() {
         this.squashMap.put(CraftInTableTask.class, new CraftSquasher());
         this.squashMap.put(UpgradeInSmithingTableTask.class, new SmithingSquasher());
      }

      public void addTask(ResourceTask t) {
         Class type = t.getClass();
         if (this.squashMap.containsKey(type)) {
            this.squashMap.get(type).add(t);
         } else {
            this.unSquashableTasks.add(t);
         }
      }

      public void addTasks(List<ResourceTask> tasks) {
         for (ResourceTask task : tasks) {
            this.addTask(task);
         }
      }

      public List<ResourceTask> getSquashed() {
         List<ResourceTask> result = new ArrayList<>();

         for (Class type : this.squashMap.keySet()) {
            result.addAll(this.squashMap.get(type).getSquashed());
         }

         result.addAll(this.unSquashableTasks);
         return result;
      }
   }
}
