package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.MiningRequirement;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectWheatSeedsTask extends ResourceTask {
   private final int count;

   public CollectWheatSeedsTask(int count) {
      super(Items.WHEAT_SEEDS, count);
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
      return (Task)(mod.getBlockScanner().anyFound(Blocks.WHEAT)
         ? new CollectCropTask(Items.AIR, 999, Blocks.WHEAT, Items.WHEAT_SEEDS)
         : new MineAndCollectTask(Items.WHEAT_SEEDS, this.count, new Block[]{Blocks.GRASS, Blocks.TALL_GRASS}, MiningRequirement.HAND));
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectWheatSeedsTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " wheat seeds.";
   }
}
