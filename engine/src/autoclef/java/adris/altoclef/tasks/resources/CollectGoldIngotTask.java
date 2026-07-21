package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.container.CraftInTableTask;
import adris.altoclef.tasks.container.SmeltInFurnaceTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.CraftingRecipe;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.RecipeTarget;
import adris.altoclef.util.SmeltTarget;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectGoldIngotTask extends ResourceTask {
   private final int count;

   public CollectGoldIngotTask(int count) {
      super(Items.GOLD_INGOT, count);
      this.count = count;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
      mod.getBehaviour().push();
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (WorldHelper.getCurrentDimension(mod) == Dimension.OVERWORLD) {
         return new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(Items.GOLD_INGOT, this.count), new ItemTarget(Items.RAW_GOLD, this.count)));
      } else if (WorldHelper.getCurrentDimension(mod) == Dimension.NETHER) {
         int nuggs = mod.getItemStorage().getItemCount(Items.GOLD_NUGGET);
         int nuggs_needed = this.count * 9 - mod.getItemStorage().getItemCount(Items.GOLD_INGOT) * 9;
         if (nuggs >= nuggs_needed) {
            ItemTarget n = new ItemTarget(Items.GOLD_NUGGET);
            CraftingRecipe recipe = CraftingRecipe.newShapedRecipe("gold_ingot", new ItemTarget[]{n, n, n, n, n, n, n, n, n}, 1);
            return new CraftInTableTask(new RecipeTarget(Items.GOLD_INGOT, this.count, recipe));
         } else {
            return new MineAndCollectTask(new ItemTarget(Items.GOLD_NUGGET, this.count * 9), new Block[]{Blocks.NETHER_GOLD_ORE}, MiningRequirement.WOOD);
         }
      } else {
         return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
      mod.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectGoldIngotTask && ((CollectGoldIngotTask)other).count == this.count;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " gold.";
   }
}
