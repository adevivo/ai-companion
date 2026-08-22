package com.player2.playerengine.tasks.resources.wood;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.resources.CraftWithMatchingPlanksTask;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectWoodenTrapDoorTask extends CraftWithMatchingPlanksTask {
   public CollectWoodenTrapDoorTask(Item[] targets, ItemTarget planks, int count) {
      super(targets, woodItems -> woodItems.trapdoor, createRecipe(planks), new boolean[]{true, true, true, true, true, true, false, false, false}, count);
   }

   public CollectWoodenTrapDoorTask(Item target, String plankCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
   }

   public CollectWoodenTrapDoorTask(int count) {
      this(ItemHelper.WOOD_TRAPDOOR, TaskCatalogue.getItemTarget("planks", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget planks) {
      ItemTarget o = null;
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{planks, planks, planks, planks, planks, planks, o, o, o}, 2);
   }
}
