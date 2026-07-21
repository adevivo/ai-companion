package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.container.CraftInTableTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.CraftingRecipe;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.RecipeTarget;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectHayBlockTask extends ResourceTask {
   private final int count;

   public CollectHayBlockTask(int count) {
      super(Items.HAY_BLOCK, count);
      this.count = count;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (mod.getBlockScanner().anyFound(Blocks.HAY_BLOCK)) {
         return new MineAndCollectTask(Items.HAY_BLOCK, this.count, new Block[]{Blocks.HAY_BLOCK}, MiningRequirement.HAND);
      } else {
         ItemTarget w = new ItemTarget(Items.WHEAT, 1);
         return new CraftInTableTask(
            new RecipeTarget(Items.HAY_BLOCK, this.count, CraftingRecipe.newShapedRecipe("hay_block", new ItemTarget[]{w, w, w, w, w, w, w, w, w}, 1))
         );
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectHayBlockTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " hay blocks.";
   }
}
