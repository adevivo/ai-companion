package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.GetCloseToBlockTask;
import adris.altoclef.tasks.movement.SearchChunkForBlockTask;
import adris.altoclef.tasks.squashed.CataloguedResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.WorldHelper;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class CollectHoneycombTask extends ResourceTask {
   private final boolean campfire;
   private final int count;
   private BlockPos nest;

   public CollectHoneycombTask(int targetCount) {
      super(Items.HONEYCOMB, targetCount);
      this.campfire = true;
      this.count = targetCount;
   }

   public CollectHoneycombTask(int targetCount, boolean useCampfire) {
      super(Items.HONEYCOMB, targetCount);
      this.campfire = useCampfire;
      this.count = targetCount;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
      mod.getBehaviour().push();
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (this.nest == null) {
         Optional<BlockPos> getNearestNest = mod.getBlockScanner().getNearestBlock(Blocks.BEE_NEST);
         if (getNearestNest.isPresent()) {
            this.nest = getNearestNest.get();
         }
      }

      if (this.nest == null) {
         if (this.campfire && !mod.getItemStorage().hasItemInventoryOnly(Items.CAMPFIRE)) {
            this.setDebugState("Can't find nest, getting campfire first...");
            return new CataloguedResourceTask(new ItemTarget(Items.CAMPFIRE, 1));
         } else {
            this.setDebugState("Alright, we're searching");
            return new SearchChunkForBlockTask(Blocks.BEE_NEST);
         }
      } else if (this.campfire && !this.isCampfireUnderNest(mod, this.nest)) {
         if (!mod.getItemStorage().hasItemInventoryOnly(Items.CAMPFIRE)) {
            this.setDebugState("Getting a campfire");
            return new CataloguedResourceTask(new ItemTarget(Items.CAMPFIRE, 1));
         } else {
            this.setDebugState("Placing campfire");
            return new PlaceBlockTask(this.nest.below(2), Blocks.CAMPFIRE);
         }
      } else if (!mod.getItemStorage().hasItemInventoryOnly(Items.SHEARS)) {
         this.setDebugState("Getting shears");
         return new CataloguedResourceTask(new ItemTarget(Items.SHEARS, 1));
      } else if ((Integer)mod.getWorld().getBlockState(this.nest).getValue(BlockStateProperties.LEVEL_HONEY) != 5) {
         if (!this.nest.closerToCenterThan(mod.getPlayer().position(), 20.0)) {
            this.setDebugState("Getting close to nest");
            return new GetCloseToBlockTask(this.nest);
         } else {
            this.setDebugState("Waiting for nest to get honey...");
            return null;
         }
      } else {
         return new InteractWithBlockTask(Items.SHEARS, this.nest);
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
      mod.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectHoneycombTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " Honeycombs " + (this.campfire ? "Peacefully" : "Recklessly");
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   private boolean isCampfireUnderNest(AltoClefController mod, BlockPos pos) {
      for (BlockPos underPos : WorldHelper.scanRegion(pos.below(6), pos.below())) {
         if (mod.getWorld().getBlockState(underPos).getBlock() == Blocks.CAMPFIRE) {
            return true;
         }
      }

      return false;
   }
}
