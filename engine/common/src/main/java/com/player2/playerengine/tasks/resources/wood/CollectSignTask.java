package com.player2.playerengine.tasks.resources.wood;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.resources.CraftWithMatchingPlanksTask;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectSignTask extends CraftWithMatchingPlanksTask {
   public CollectSignTask(Item[] targets, ItemTarget planks, int count) {
      super(targets, woodItems -> woodItems.sign, createRecipe(planks), new boolean[]{true, true, true, true, true, true, false, false, false}, count);
   }

   public CollectSignTask(Item target, String plankCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
   }

   public CollectSignTask(int count) {
      this(ItemHelper.WOOD_SIGN, TaskCatalogue.getItemTarget("planks", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget planks) {
      ItemTarget stick = TaskCatalogue.getItemTarget("stick", 1);
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{planks, planks, planks, planks, planks, planks, null, stick, null}, 3);
   }
}
