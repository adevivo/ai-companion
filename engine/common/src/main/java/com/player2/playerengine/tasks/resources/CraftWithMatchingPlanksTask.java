package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import java.util.function.Function;
import net.minecraft.world.item.Item;

public class CraftWithMatchingPlanksTask extends CraftWithMatchingMaterialsTask {
   private final ItemTarget visualTarget;
   private final Function<ItemHelper.WoodItems, Item> getTargetItem;

   public CraftWithMatchingPlanksTask(
      Item[] validTargets, Function<ItemHelper.WoodItems, Item> getTargetItem, CraftingRecipe recipe, boolean[] sameMask, int count
   ) {
      super(new ItemTarget(validTargets, count), recipe, sameMask);
      this.getTargetItem = getTargetItem;
      this.visualTarget = new ItemTarget(validTargets, count);
   }

   @Override
   protected int getExpectedTotalCountOfSameItem(PlayerEngineController mod, Item sameItem) {
      return mod.getItemStorage().getItemCount(sameItem) + mod.getItemStorage().getItemCount(ItemHelper.planksToLog(sameItem)) * 4;
   }

   @Override
   protected Task getSpecificSameResourceTask(PlayerEngineController mod, Item[] toGet) {
      for (Item plankToGet : toGet) {
         Item log = ItemHelper.planksToLog(plankToGet);
         if (mod.getItemStorage().getItemCount(log) >= 1) {
            return TaskCatalogue.getItemTask(plankToGet, 1);
         }
      }

      Debug.logError("CraftWithMatchingPlanks: Should never happen!");
      return null;
   }

   @Override
   protected Item getSpecificItemCorrespondingToMajorityResource(Item majority) {
      for (ItemHelper.WoodItems woodItems : ItemHelper.getWoodItems()) {
         if (woodItems.planks == majority) {
            return this.getTargetItem.apply(woodItems);
         }
      }

      return null;
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CraftWithMatchingPlanksTask task ? task.visualTarget.equals(this.visualTarget) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Crafting: " + this.visualTarget;
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }
}
