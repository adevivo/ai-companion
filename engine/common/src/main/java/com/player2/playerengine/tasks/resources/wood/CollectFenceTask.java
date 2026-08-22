package com.player2.playerengine.tasks.resources.wood;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.resources.CraftWithMatchingPlanksTask;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectFenceTask extends CraftWithMatchingPlanksTask {
   public CollectFenceTask(Item[] targets, ItemTarget planks, int count) {
      super(targets, woodItems -> woodItems.fence, createRecipe(planks), new boolean[]{true, false, true, true, false, true, false, false, false}, count);
   }

   public CollectFenceTask(Item target, String plankCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
   }

   public CollectFenceTask(int count) {
      this(ItemHelper.WOOD_FENCE, TaskCatalogue.getItemTarget("planks", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget planks) {
      ItemTarget s = TaskCatalogue.getItemTarget("stick", 1);
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{planks, s, planks, planks, s, planks, null, null, null}, 3);
   }
}
