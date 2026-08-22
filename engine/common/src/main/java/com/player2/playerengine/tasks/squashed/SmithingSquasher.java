package com.player2.playerengine.tasks.squashed;

import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.container.UpgradeInSmithingTableTask;
import com.player2.playerengine.util.ItemTarget;
import java.util.ArrayList;
import java.util.List;

public class SmithingSquasher extends TypeSquasher<UpgradeInSmithingTableTask> {
   @Override
   protected List<ResourceTask> getSquashed(List<UpgradeInSmithingTableTask> tasks) {
      if (tasks.isEmpty()) {
         return new ArrayList<>();
      } else {
         List<ResourceTask> result = new ArrayList<>();
         List<ItemTarget> materialsToCollect = new ArrayList<>();

         for (UpgradeInSmithingTableTask task : tasks) {
            materialsToCollect.add(task.getMaterials());
            materialsToCollect.add(task.getTools());
            materialsToCollect.add(task.getTemplate());
         }

         if (!materialsToCollect.isEmpty()) {
            result.add(new CataloguedResourceTask(materialsToCollect.toArray(new ItemTarget[0])));
         }

         result.addAll(tasks);
         return result;
      }
   }
}
