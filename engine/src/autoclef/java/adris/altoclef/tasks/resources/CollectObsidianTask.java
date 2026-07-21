package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.construction.PlaceObsidianBucketTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import adris.altoclef.util.time.TimerGame;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectObsidianTask extends ResourceTask {
   private final TimerGame placeWaterTimeout = new TimerGame(6.0);
   private final MovementProgressChecker lavaTimeout = new MovementProgressChecker();
   private final Set<BlockPos> lavaBlacklist = new HashSet<>();
   private final int count;
   private Task forceCompleteTask = null;
   private BlockPos lavaWaitCurrentPos;
   private PlaceObsidianBucketTask placeObsidianTask;

   public CollectObsidianTask(int count) {
      super(Items.OBSIDIAN, count);
      this.count = count;
   }

   private static BlockPos getLavaStructurePos(BlockPos lavaPos) {
      return lavaPos.offset(1, 1, 0);
   }

   private static BlockPos getLavaWaterPos(BlockPos lavaPos) {
      return lavaPos.above();
   }

   private static BlockPos getGoodObsidianPosition(AltoClefController mod) {
      BlockPos start = mod.getPlayer().blockPosition().offset(-3, -3, -3);
      BlockPos end = mod.getPlayer().blockPosition().offset(3, 3, 3);

      for (BlockPos pos : WorldHelper.scanRegion(start, end)) {
         if (!WorldHelper.canBreak(mod, pos) || !WorldHelper.canPlace(mod, pos)) {
            return null;
         }
      }

      return mod.getPlayer().blockPosition();
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
      mod.getBehaviour().push();
      mod.getBehaviour().setRayTracingFluidHandling(Fluid.SOURCE_ONLY);
      mod.getBehaviour()
         .avoidBlockPlacing(
            pos -> this.lavaWaitCurrentPos != null ? pos.equals(this.lavaWaitCurrentPos) || pos.equals(getLavaWaterPos(this.lavaWaitCurrentPos)) : false
         );
      mod.getBehaviour()
         .avoidBlockBreaking((Predicate<BlockPos>)(pos -> this.lavaWaitCurrentPos != null ? pos.equals(getLavaStructurePos(this.lavaWaitCurrentPos)) : false));
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (this.lavaWaitCurrentPos != null
         && mod.getChunkTracker().isChunkLoaded(this.lavaWaitCurrentPos)
         && mod.getWorld().getBlockState(this.lavaWaitCurrentPos).getBlock() != Blocks.LAVA) {
         this.lavaWaitCurrentPos = null;
      }

      if (!StorageHelper.miningRequirementMet(this.controller, MiningRequirement.DIAMOND)) {
         this.setDebugState("Getting diamond pickaxe first");
         return new SatisfyMiningRequirementTask(MiningRequirement.DIAMOND);
      } else if (this.forceCompleteTask != null && this.forceCompleteTask.isActive() && !this.forceCompleteTask.isFinished()) {
         return this.forceCompleteTask;
      } else {
         Predicate<BlockPos> goodObsidian = blockPos -> blockPos.closerToCenterThan(mod.getPlayer().position(), 800.0) && WorldHelper.canBreak(mod, blockPos);
         if (mod.getBlockScanner().anyFound(goodObsidian, Blocks.OBSIDIAN) || mod.getEntityTracker().itemDropped(Items.OBSIDIAN)) {
            this.setDebugState("Mining/Collecting obsidian");
            this.placeObsidianTask = null;
            return new MineAndCollectTask(new ItemTarget(Items.OBSIDIAN, this.count), new Block[]{Blocks.OBSIDIAN}, MiningRequirement.DIAMOND);
         } else if (WorldHelper.getCurrentDimension(mod) == Dimension.NETHER) {
            double AVERAGE_GOLD_PER_OBSIDIAN = 11.475;
            int gold_buffer = (int)(11.475 * this.count);
            this.setDebugState("We can't place water, so we're trading for obsidian");
            return new TradeWithPiglinsTask(gold_buffer, Items.OBSIDIAN, this.count);
         } else {
            if (this.placeObsidianTask == null) {
               BlockPos goodPos = getGoodObsidianPosition(mod);
               if (goodPos == null) {
                  this.setDebugState("Walking until we find a spot to place obsidian");
                  return new TimeoutWanderTask();
               }

               this.placeObsidianTask = new PlaceObsidianBucketTask(goodPos);
            }

            if (this.placeObsidianTask != null
               && !mod.getItemStorage().hasItem(Items.LAVA_BUCKET)
               && !this.placeObsidianTask.getPos().closerToCenterThan(mod.getPlayer().position(), 4.0)) {
               BlockPos goodPos = getGoodObsidianPosition(mod);
               if (goodPos != null) {
                  Debug.logMessage("(nudged obsidian target closer)");
                  this.placeObsidianTask = new PlaceObsidianBucketTask(goodPos);
               }
            }

            this.setDebugState("Placing Obsidian");
            return this.placeObsidianTask;
         }
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
      mod.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectObsidianTask task ? task.count == this.count : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Collect " + this.count + " blocks of obsidian";
   }
}
