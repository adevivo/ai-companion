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

public class CraftWithMatchingStrippedLogsTask extends CraftWithMatchingMaterialsTask {
   private final ItemTarget visualTarget;
   private final Function<ItemHelper.WoodItems, Item> getTargetItem;

   public CraftWithMatchingStrippedLogsTask(
      Item[] validTargets, Function<ItemHelper.WoodItems, Item> getTargetItem, CraftingRecipe recipe, boolean[] sameMask, int count
   ) {
      super(new ItemTarget(validTargets, count), recipe, sameMask);
      this.getTargetItem = getTargetItem;
      this.visualTarget = new ItemTarget(validTargets, count);
   }

   @Override
   protected Task getSpecificSameResourceTask(PlayerEngineController mod, Item[] toGet) {
      for (Item strippedLogToGet : toGet) {
         Item log = ItemHelper.strippedToLogs(strippedLogToGet);
         if (mod.getItemStorage().getItemCount(log) >= 1) {
            return TaskCatalogue.getItemTask(strippedLogToGet, 1);
         }
      }

      Debug.logError("CraftWithMatchingStrippedLogs: Should never happen!");
      return null;
   }

   @Override
   protected Item getSpecificItemCorrespondingToMajorityResource(Item majority) {
      for (ItemHelper.WoodItems woodItems : ItemHelper.getWoodItems()) {
         if (woodItems.strippedLog == majority) {
            return this.getTargetItem.apply(woodItems);
         }
      }

      return null;
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CraftWithMatchingStrippedLogsTask task ? task.visualTarget.equals(this.visualTarget) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Getting: " + this.visualTarget;
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }
}
