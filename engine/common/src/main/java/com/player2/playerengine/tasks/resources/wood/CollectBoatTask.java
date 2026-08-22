package com.player2.playerengine.tasks.resources.wood;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.resources.CraftWithMatchingPlanksTask;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectBoatTask extends CraftWithMatchingPlanksTask {
   public CollectBoatTask(Item[] targets, ItemTarget planks, int count) {
      super(targets, woodItems -> woodItems.boat, createRecipe(planks), new boolean[]{true, false, true, true, true, true, false, false, false}, count);
   }

   public CollectBoatTask(Item target, String plankCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
   }

   public CollectBoatTask(int count) {
      this(ItemHelper.WOOD_BOAT, TaskCatalogue.getItemTarget("planks", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget planks) {
      ItemTarget o = null;
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{planks, o, planks, planks, planks, planks, o, o, o}, 1);
   }
}
