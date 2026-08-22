package com.player2.playerengine.tasks.resources.wood;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.resources.CraftWithMatchingStrippedLogsTask;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;

public class CollectHangingSignTask extends CraftWithMatchingStrippedLogsTask {
   public CollectHangingSignTask(Item[] targets, ItemTarget strippedLogs, int count) {
      super(
         targets, woodItems -> woodItems.hangingSign, createRecipe(strippedLogs), new boolean[]{false, false, false, true, true, true, true, true, true}, count
      );
   }

   public CollectHangingSignTask(Item target, String strippedLogCatalogueName, int count) {
      this(new Item[]{target}, new ItemTarget(strippedLogCatalogueName, 1), count);
   }

   public CollectHangingSignTask(int count) {
      this(ItemHelper.WOOD_HANGING_SIGN, TaskCatalogue.getItemTarget("stripped_logs", 1), count);
   }

   private static CraftingRecipe createRecipe(ItemTarget strippedLogs) {
      ItemTarget chain = TaskCatalogue.getItemTarget("chain", 1);
      return CraftingRecipe.newShapedRecipe(
         new ItemTarget[]{chain, null, chain, strippedLogs, strippedLogs, strippedLogs, strippedLogs, strippedLogs, strippedLogs}, 6
      );
   }
}
