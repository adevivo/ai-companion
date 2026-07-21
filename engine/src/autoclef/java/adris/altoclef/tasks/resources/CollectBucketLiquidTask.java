package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.DoToClosestBlockTask;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetCloseToBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import java.util.HashSet;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectBucketLiquidTask extends ResourceTask {
   private final HashSet<BlockPos> blacklist = new HashSet<>();
   private final TimerGame tryImmediatePickupTimer = new TimerGame(3.0);
   private final TimerGame pickedUpTimer = new TimerGame(0.5);
   private final int count;
   private final Item target;
   private final Block toCollect;
   private final String liquidName;
   private final MovementProgressChecker progressChecker = new MovementProgressChecker();
   private boolean wasWandering = false;
   int tries = 0;
   TimerGame timeoutTimer = new TimerGame(2.0);

   public CollectBucketLiquidTask(String liquidName, Item filledBucket, int targetCount, Block toCollect) {
      super(filledBucket, targetCount);
      this.liquidName = liquidName;
      this.target = filledBucket;
      this.count = targetCount;
      this.toCollect = toCollect;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
      mod.getBehaviour().push();
      mod.getBehaviour().setRayTracingFluidHandling(Fluid.SOURCE_ONLY);
      mod.getBehaviour().avoidBlockBreaking((Predicate<BlockPos>)(pos -> this.controller.getWorld().getBlockState(pos).getBlock() == this.toCollect));
      mod.getBehaviour().avoidBlockPlacing(pos -> this.controller.getWorld().getBlockState(pos).getBlock() == this.toCollect);
      mod.getBaritoneSettings().avoidUpdatingFallingBlocks.set(Boolean.TRUE);
      this.progressChecker.reset();
   }

   @Override
   protected Task onTick() {
      Task result = super.onTick();
      if (!this.thisOrChildAreTimedOut()) {
         this.wasWandering = false;
      }

      return result;
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (mod.getBaritone().getPathingBehavior().isPathing()) {
         this.progressChecker.reset();
      }

      if (this.tryImmediatePickupTimer.elapsed() && !mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
         Block standingInside = mod.getWorld().getBlockState(mod.getPlayer().blockPosition()).getBlock();
         if (standingInside == this.toCollect && WorldHelper.isSourceBlock(this.controller, mod.getPlayer().blockPosition(), false)) {
            this.setDebugState("Trying to collect (we are in it)");
            mod.getInputControls().forceLook(0.0F, 90.0F);
            this.tryImmediatePickupTimer.reset();
            if (mod.getSlotHandler().forceEquipItem(Items.BUCKET)) {
               mod.getInputControls().tryPress(Input.CLICK_RIGHT);
               mod.getExtraBaritoneSettings().setInteractionPaused(true);
               this.pickedUpTimer.reset();
               this.progressChecker.reset();
            }

            return null;
         }
      }

      if (!this.pickedUpTimer.elapsed()) {
         mod.getExtraBaritoneSettings().setInteractionPaused(false);
         this.progressChecker.reset();
         return null;
      } else {
         int bucketsNeeded = this.count - mod.getItemStorage().getItemCount(Items.BUCKET) - mod.getItemStorage().getItemCount(this.target);
         if (bucketsNeeded > 0) {
            this.setDebugState("Getting bucket...");
            return TaskCatalogue.getItemTask(Items.BUCKET, bucketsNeeded);
         } else {
            Predicate<BlockPos> isSafeSourceLiquid = blockPos -> {
               if (this.blacklist.contains(blockPos)) {
                  return false;
               } else if (!WorldHelper.canReach(this.controller, blockPos)) {
                  return false;
               } else if (!WorldHelper.canReach(this.controller, blockPos.above())) {
                  return false;
               } else {
                  assert this.controller.getWorld() != null;

                  Block above = mod.getWorld().getBlockState(blockPos.above()).getBlock();
                  if (above != Blocks.BEDROCK && above != Blocks.WATER) {
                     for (Direction direction : Direction.values()) {
                        if (!direction.getAxis().isVertical() && mod.getWorld().getBlockState(blockPos.above().relative(direction)).getBlock() == Blocks.WATER) {
                           return false;
                        }
                     }

                     return WorldHelper.isSourceBlock(this.controller, blockPos, false);
                  } else {
                     return false;
                  }
               }
            };
            if (mod.getBlockScanner().anyFound(isSafeSourceLiquid, this.toCollect)) {
               this.setDebugState("Trying to collect...");
               return new DoToClosestBlockTask(blockPos -> {
                  if (mod.getWorld().getBlockState(blockPos.above()).isSolid()) {
                     if (!this.progressChecker.check(mod)) {
                        mod.getBaritone().getPathingBehavior().cancelEverything();
                        mod.getBaritone().getPathingBehavior().forceCancel();
                        mod.getBaritone().getExploreProcess().onLostControl();
                        mod.getBaritone().getCustomGoalProcess().onLostControl();
                        Debug.logMessage("Failed to break, blacklisting.");
                        mod.getBlockScanner().requestBlockUnreachable(blockPos);
                        this.blacklist.add(blockPos);
                     }

                     return new DestroyBlockTask(blockPos.above());
                  } else if (this.tries > 75) {
                     if (this.timeoutTimer.elapsed()) {
                        this.tries = 0;
                     }

                     mod.log("trying to wander " + this.timeoutTimer.getDuration());
                     return new TimeoutWanderTask();
                  } else {
                     this.timeoutTimer.reset();
                     if (LookHelper.getReach(this.controller, blockPos).isPresent() && mod.getBaritone().getPathingBehavior().isSafeToCancel()) {
                        this.tries++;
                        return new InteractWithBlockTask(new ItemTarget(Items.BUCKET, 1), blockPos, this.toCollect != Blocks.LAVA, new Vec3i(0, 1, 0));
                     } else {
                        if (this.thisOrChildAreTimedOut() && !this.wasWandering) {
                           mod.getBlockScanner().requestBlockUnreachable(blockPos.above());
                           this.wasWandering = true;
                        }

                        return new GetCloseToBlockTask(blockPos.above());
                     }
                  }
               }, isSafeSourceLiquid, this.toCollect);
            } else if (this.toCollect == Blocks.WATER && WorldHelper.getCurrentDimension(this.controller) == Dimension.NETHER) {
               return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
            } else {
               this.setDebugState("Searching for liquid by wandering around aimlessly");
               return new TimeoutWanderTask();
            }
         }
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
      mod.getBehaviour().pop();
      mod.getExtraBaritoneSettings().setInteractionPaused(false);
      mod.getBaritoneSettings().avoidUpdatingFallingBlocks.set(false);
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      if (other instanceof CollectBucketLiquidTask task) {
         return task.count != this.count ? false : task.toCollect == this.toCollect;
      } else {
         return false;
      }
   }

   @Override
   protected String toDebugStringName() {
      return "Collect " + this.count + " " + this.liquidName + " buckets";
   }

   public static class CollectLavaBucketTask extends CollectBucketLiquidTask {
      public CollectLavaBucketTask(int targetCount) {
         super("lava", Items.LAVA_BUCKET, targetCount, Blocks.LAVA);
      }
   }

   public static class CollectWaterBucketTask extends CollectBucketLiquidTask {
      public CollectWaterBucketTask(int targetCount) {
         super("water", Items.WATER_BUCKET, targetCount, Blocks.WATER);
      }
   }
}
