package com.player2.playerengine.tasks.squashed;

import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.container.CraftInTableTask;
import com.player2.playerengine.util.RecipeTarget;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CraftSquasher extends TypeSquasher<CraftInTableTask> {
   @Override
   protected List<ResourceTask> getSquashed(List<CraftInTableTask> tasks) {
      List<RecipeTarget> targetRecipies = new ArrayList<>();

      for (CraftInTableTask task : tasks) {
         targetRecipies.addAll(Arrays.asList(task.getRecipeTargets()));
      }

      return Collections.singletonList(new CraftInTableTask(targetRecipies.toArray(RecipeTarget[]::new)));
   }
}
