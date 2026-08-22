package com.player2.playerengine.tasks.resources.wood;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.resources.CraftWithMatchingPlanksTask;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectFenceGateTask extends CraftWithMatchingPlanksTask {
   public CollectFenceGateTask(Item[] targets, ItemTarget planks, int count) {
      super(targets, woodItems -> woodItems.fenceGate, createRecipe(planks), new boolean[]{false, true, false, false, true, false, false, false, false}, count);
   }

   public CollectFenceGateTask(Item target, String plankCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
   }

   public CollectFenceGateTask(int count) {
      this(ItemHelper.WOOD_FENCE_GATE, TaskCatalogue.getItemTarget("planks", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget planks) {
      ItemTarget s = TaskCatalogue.getItemTarget("stick", 1);
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{s, planks, s, s, planks, s, null, null, null}, 1);
   }
}
