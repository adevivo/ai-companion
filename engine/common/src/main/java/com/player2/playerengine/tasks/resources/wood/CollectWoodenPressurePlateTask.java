package com.player2.playerengine.tasks.resources.wood;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.resources.CraftWithMatchingPlanksTask;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectWoodenPressurePlateTask extends CraftWithMatchingPlanksTask {
   public CollectWoodenPressurePlateTask(Item[] targets, ItemTarget planks, int count) {
      super(targets, woodItems -> woodItems.pressurePlate, createRecipe(planks), new boolean[]{true, true, false, false}, count);
   }

   public CollectWoodenPressurePlateTask(Item target, String plankCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
   }

   public CollectWoodenPressurePlateTask(int count) {
      this(ItemHelper.WOOD_PRESSURE_PLATE, TaskCatalogue.getItemTarget("planks", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget planks) {
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{planks, planks, null, null}, 1);
   }
}
