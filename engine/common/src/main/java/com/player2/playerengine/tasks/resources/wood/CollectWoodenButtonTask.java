package com.player2.playerengine.tasks.resources.wood;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.resources.CraftWithMatchingPlanksTask;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectWoodenButtonTask extends CraftWithMatchingPlanksTask {
   public CollectWoodenButtonTask(Item[] targets, ItemTarget planks, int count) {
      super(targets, woodItems -> woodItems.button, createRecipe(planks), new boolean[]{true, true, false, false}, count);
   }

   public CollectWoodenButtonTask(Item target, String plankCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
   }

   public CollectWoodenButtonTask(int count) {
      this(ItemHelper.WOOD_BUTTON, TaskCatalogue.getItemTarget("planks", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget planks) {
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{planks, null, null, null}, 1);
   }
}
