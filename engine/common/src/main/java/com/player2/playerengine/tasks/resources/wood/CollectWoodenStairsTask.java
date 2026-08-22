package com.player2.playerengine.tasks.resources.wood;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.resources.CraftWithMatchingPlanksTask;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectWoodenStairsTask extends CraftWithMatchingPlanksTask {
   public CollectWoodenStairsTask(Item[] targets, ItemTarget planks, int count) {
      super(targets, woodItems -> woodItems.stairs, createRecipe(planks), new boolean[]{true, false, false, true, true, false, true, true, true}, count);
   }

   public CollectWoodenStairsTask(Item target, String plankCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
   }

   public CollectWoodenStairsTask(int count) {
      this(ItemHelper.WOOD_STAIRS, TaskCatalogue.getItemTarget("planks", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget planks) {
      ItemTarget o = null;
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{planks, o, o, planks, planks, o, planks, planks, planks}, 4);
   }
}
