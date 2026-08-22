package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class CollectBedTask extends CraftWithMatchingWoolTask {
   public static final Block[] BEDS = ItemHelper.itemsToBlocks(ItemHelper.BED);
   private final ItemTarget visualBedTarget;

   public CollectBedTask(Item[] beds, ItemTarget wool, int count) {
      super(
         new ItemTarget(beds, count),
         colorfulItems -> colorfulItems.wool,
         colorfulItems -> colorfulItems.bed,
         createBedRecipe(wool),
         new boolean[]{true, true, true, false, false, false, false, false, false}
      );
      this.visualBedTarget = new ItemTarget(beds, count);
   }

   public CollectBedTask(Item bed, String woolCatalogueName, int count) {
      this(new Item[]{bed}, new ItemTarget(woolCatalogueName, 1), count);
   }

   public CollectBedTask(int count) {
      this(ItemHelper.BED, TaskCatalogue.getItemTarget("wool", 1), count);
   }

   private static CraftingRecipe createBedRecipe(ItemTarget wool) {
      ItemTarget p = TaskCatalogue.getItemTarget("planks", 1);
      return CraftingRecipe.newShapedRecipe(new ItemTarget[]{wool, wool, wool, p, p, p, null, null, null}, 1);
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }

   @Override
   protected Task onResourceTick(PlayerEngineController mod) {
      return (Task)(mod.getBlockScanner().anyFound(BEDS)
         ? new MineAndCollectTask(new ItemTarget(ItemHelper.BED, 1), BEDS, MiningRequirement.HAND)
         : super.onResourceTick(mod));
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectBedTask task ? task.visualBedTarget.equals(this.visualBedTarget) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Crafting bed: " + this.visualBedTarget;
   }
}
