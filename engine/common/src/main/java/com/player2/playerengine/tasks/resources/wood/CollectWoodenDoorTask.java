package com.player2.playerengine.tasks.resources.wood;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.resources.CraftWithMatchingPlanksTask;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectWoodenDoorTask extends CraftWithMatchingPlanksTask {
   public CollectWoodenDoorTask(Item[] targets, ItemTarget planks, int count) {
      super(targets, woodItems -> woodItems.door, createRecipe(planks), new boolean[]{true, true, false, true, true, false, true, true, false}, count);
   }

   public CollectWoodenDoorTask(Item target, String plankCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
   }

   public CollectWoodenDoorTask(int count) {
      this(ItemHelper.WOOD_DOOR, TaskCatalogue.getItemTarget("planks", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget planks) {
      ItemTarget o = null;
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{planks, planks, o, planks, planks, o, planks, planks, o}, 3);
   }
}
