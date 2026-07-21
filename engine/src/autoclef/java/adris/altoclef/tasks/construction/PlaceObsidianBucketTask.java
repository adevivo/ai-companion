package adris.altoclef.tasks.construction;

import adris.altoclef.AltoClefController;
import adris.altoclef.BotBehaviour;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commands.BlockScanner;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class PlaceObsidianBucketTask extends Task {
   public static final Vec3i[] CAST_FRAME = new Vec3i[]{
      new Vec3i(0, -1, 0),
      new Vec3i(0, -1, -1),
      new Vec3i(0, -1, 1),
      new Vec3i(-1, -1, 0),
      new Vec3i(1, -1, 0),
      new Vec3i(0, 0, -1),
      new Vec3i(0, 0, 1),
      new Vec3i(-1, 0, 0),
      new Vec3i(1, 0, 0),
      new Vec3i(1, 1, 0)
   };
   private final MovementProgressChecker progressChecker = new MovementProgressChecker();
   private final BlockPos pos;
   private BlockPos currentCastTarget;
   private BlockPos currentDestroyTarget;

   public PlaceObsidianBucketTask(BlockPos pos) {
      this.pos = pos;
   }

   @Override
   protected void onStart() {
      BotBehaviour botBehaviour = this.controller.getBehaviour();
      botBehaviour.push();
      botBehaviour.avoidBlockBreaking(this::isBlockInCastFrame);
      botBehaviour.avoidBlockPlacing(this::isBlockInCastWaterOrLava);
      this.progressChecker.reset();
      Debug.logInternal("Started onStart method");
      Debug.logInternal("Behaviour pushed");
      Debug.logInternal("Avoiding block breaking");
      Debug.logInternal("Avoiding block placing");
      Debug.logInternal("Progress checker reset");
   }

   private boolean isBlockInCastFrame(BlockPos block) {
      return Arrays.stream(CAST_FRAME).map(this.pos::offset).anyMatch(block::equals);
   }

   private boolean isBlockInCastWaterOrLava(BlockPos blockPos) {
      BlockPos waterTarget = this.pos.above();
      Debug.logInternal("blockPos: " + blockPos);
      Debug.logInternal("waterTarget: " + waterTarget);
      return blockPos.equals(this.pos) || blockPos.equals(waterTarget);
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      if (mod.getBaritone().getPathingBehavior().isPathing()) {
         this.progressChecker.reset();
      }

      if (mod.getBlockScanner().isBlockAtPosition(this.pos, Blocks.OBSIDIAN) && mod.getBlockScanner().isBlockAtPosition(this.pos.above(), Blocks.WATER)) {
         return new ClearLiquidTask(this.pos.above());
      } else if (!mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
         this.progressChecker.reset();
         return TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1);
      } else if (!mod.getItemStorage().hasItem(Items.LAVA_BUCKET) && !mod.getBlockScanner().isBlockAtPosition(this.pos, Blocks.LAVA)) {
         this.progressChecker.reset();
         return TaskCatalogue.getItemTask(Items.LAVA_BUCKET, 1);
      } else if (!this.progressChecker.check(mod)) {
         mod.getBaritone().getPathingBehavior().forceCancel();
         mod.getBlockScanner().requestBlockUnreachable(this.pos);
         this.progressChecker.reset();
         return new TimeoutWanderTask(5.0F);
      } else {
         if (this.currentCastTarget != null) {
            if (!WorldHelper.isSolidBlock(this.controller, this.currentCastTarget)) {
               return new PlaceBlockTask(
                  this.currentCastTarget,
                  Arrays.stream(ItemHelper.itemsToBlocks(mod.getModSettings().getThrowawayItems(mod)))
                     .filter(b -> !Arrays.stream(ItemHelper.itemsToBlocks(ItemHelper.LEAVES)).toList().contains(b))
                     .toArray(Block[]::new)
               );
            }

            this.currentCastTarget = null;
         }

         if (this.currentDestroyTarget != null) {
            if (WorldHelper.isSolidBlock(this.controller, this.currentDestroyTarget)) {
               return new DestroyBlockTask(this.currentDestroyTarget);
            }

            this.currentDestroyTarget = null;
         }

         if (this.currentCastTarget != null && WorldHelper.isSolidBlock(this.controller, this.currentCastTarget)) {
            this.currentCastTarget = null;
         }

         for (Vec3i castPosRelative : CAST_FRAME) {
            BlockPos castPos = this.pos.offset(castPosRelative);
            if (!WorldHelper.isSolidBlock(this.controller, castPos)) {
               this.currentCastTarget = castPos;
               Debug.logInternal("Building cast frame...");
               return null;
            }
         }

         if (mod.getWorld().getBlockState(this.pos).getBlock() != Blocks.LAVA) {
            BlockPos targetPos = this.pos.offset(-1, 1, 0);
            if (!mod.getPlayer().blockPosition().equals(targetPos) && mod.getItemStorage().hasItem(Items.LAVA_BUCKET)) {
               Debug.logInternal("Positioning player before placing lava...");
               return new GetToBlockTask(targetPos, false);
            } else if (WorldHelper.isSolidBlock(this.controller, this.pos)) {
               Debug.logInternal("Clearing space around lava...");
               this.currentDestroyTarget = this.pos;
               return null;
            } else if (WorldHelper.isSolidBlock(this.controller, this.pos.above())) {
               Debug.logInternal("Clearing space around lava...");
               this.currentDestroyTarget = this.pos.above();
               return null;
            } else if (WorldHelper.isSolidBlock(this.controller, this.pos.above(2))) {
               Debug.logInternal("Clearing space around lava...");
               this.currentDestroyTarget = this.pos.above(2);
               return null;
            } else {
               Debug.logInternal("Placing lava for cast...");
               return new InteractWithBlockTask(new ItemTarget(Items.LAVA_BUCKET, 1), Direction.WEST, this.pos.offset(1, 0, 0), false);
            }
         } else {
            BlockPos waterCheck = this.pos.above();
            if (mod.getWorld().getBlockState(waterCheck).getBlock() != Blocks.WATER) {
               Debug.logInternal("Placing water for cast...");
               BlockPos targetPos = this.pos.offset(-1, 1, 0);
               if (!mod.getPlayer().blockPosition().equals(targetPos) && mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
                  Debug.logInternal("Positioning player before placing water...");
                  return new GetToBlockTask(targetPos, false);
               } else if (WorldHelper.isSolidBlock(this.controller, waterCheck)) {
                  this.currentDestroyTarget = waterCheck;
                  return null;
               } else if (WorldHelper.isSolidBlock(this.controller, waterCheck.above())) {
                  this.currentDestroyTarget = waterCheck.above();
                  return null;
               } else {
                  return new InteractWithBlockTask(new ItemTarget(Items.WATER_BUCKET, 1), Direction.WEST, this.pos.offset(1, 1, 0), true);
               }
            } else {
               return null;
            }
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      if (this.controller.getBehaviour() != null) {
         this.controller.getBehaviour().pop();
         Debug.logInternal("Behaviour popped.");
      }
   }

   @Override
   public boolean isFinished() {
      BlockScanner blockTracker = this.controller.getBlockScanner();
      BlockPos pos = this.pos;
      boolean isObsidian = blockTracker.isBlockAtPosition(pos, Blocks.OBSIDIAN);
      Debug.logInternal("isObsidian: " + isObsidian);
      boolean isNotWaterAbove = !blockTracker.isBlockAtPosition(pos.above(), Blocks.WATER);
      Debug.logInternal("isNotWaterAbove: " + isNotWaterAbove);
      boolean isFinished = isObsidian && isNotWaterAbove;
      Debug.logInternal("isFinished: " + isFinished);
      return isFinished;
   }

   @Override
   protected boolean isEqual(Task other) {
      if (other instanceof PlaceObsidianBucketTask task) {
         boolean isEqual = task.getPos().equals(this.getPos());
         Debug.logInternal("isEqual: " + isEqual);
         return isEqual;
      } else {
         Debug.logInternal("isEqual: false");
         return false;
      }
   }

   @Override
   protected String toDebugString() {
      return "Placing obsidian at " + this.pos + " with a cast";
   }

   public BlockPos getPos() {
      Debug.logInternal("Entering getPos()");
      return this.pos;
   }
}
