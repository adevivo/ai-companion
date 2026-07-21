package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.DoToClosestBlockTask;
import adris.altoclef.tasks.construction.compound.ConstructNetherPortalBucketTask;
import adris.altoclef.tasks.construction.compound.ConstructNetherPortalObsidianTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

public class EnterNetherPortalTask extends Task {
   private final Task getPortalTask;
   private final Dimension targetDimension;
   private final TimerGame portalTimeout = new TimerGame(10.0);
   private final TimeoutWanderTask wanderTask = new TimeoutWanderTask(5.0F);
   private final Predicate<BlockPos> goodPortal;
   private boolean leftPortal;

   public EnterNetherPortalTask(Task getPortalTask, Dimension targetDimension, Predicate<BlockPos> goodPortal) {
      if (targetDimension == Dimension.END) {
         throw new IllegalArgumentException("Can't build a nether portal to the end.");
      } else {
         this.getPortalTask = getPortalTask;
         this.targetDimension = targetDimension;
         this.goodPortal = goodPortal;
      }
   }

   public EnterNetherPortalTask(Dimension targetDimension, Predicate<BlockPos> goodPortal) {
      this(null, targetDimension, goodPortal);
   }

   public EnterNetherPortalTask(Task getPortalTask, Dimension targetDimension) {
      this(getPortalTask, targetDimension, blockPos -> true);
   }

   public EnterNetherPortalTask(Dimension targetDimension) {
      this(null, targetDimension);
   }

   @Override
   protected void onStart() {
      this.leftPortal = false;
      this.portalTimeout.reset();
      this.wanderTask.resetWander();
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      if (this.wanderTask.isActive() && !this.wanderTask.isFinished()) {
         this.setDebugState("Exiting portal for a bit.");
         this.portalTimeout.reset();
         this.leftPortal = true;
         return this.wanderTask;
      } else if (mod.getWorld().getBlockState(mod.getPlayer().blockPosition()).getBlock() == Blocks.NETHER_PORTAL) {
         if (this.portalTimeout.elapsed() && !this.leftPortal) {
            return this.wanderTask;
         } else {
            this.setDebugState("Waiting inside portal");
            mod.getBaritone().getExploreProcess().onLostControl();
            mod.getBaritone().getCustomGoalProcess().onLostControl();
            mod.getBaritone().getMineProcess().onLostControl();
            mod.getBaritone().getFarmProcess().onLostControl();
            mod.getBaritone().getGetToBlockProcess();
            mod.getBaritone().getBuilderProcess();
            mod.getBaritone().getFollowProcess();
            mod.getInputControls().release(Input.SNEAK);
            mod.getInputControls().release(Input.MOVE_BACK);
            mod.getInputControls().release(Input.MOVE_FORWARD);
            return null;
         }
      } else {
         this.portalTimeout.reset();
         Predicate<BlockPos> standablePortal = blockPos -> {
            if (mod.getWorld().getBlockState(blockPos).getBlock() == Blocks.NETHER_PORTAL) {
               return this.goodPortal.test(blockPos);
            } else if (!mod.getChunkTracker().isChunkLoaded(blockPos)) {
               return this.goodPortal.test(blockPos);
            } else {
               BlockPos below = blockPos.below();
               boolean canStand = WorldHelper.isSolidBlock(this.controller, below) && !mod.getBlockScanner().isBlockAtPosition(below, Blocks.NETHER_PORTAL);
               return canStand && this.goodPortal.test(blockPos);
            }
         };
         if (mod.getBlockScanner().anyFound(standablePortal, Blocks.NETHER_PORTAL)) {
            this.setDebugState("Going to found portal");
            return new DoToClosestBlockTask(blockPos -> new GetToBlockTask(blockPos, false), standablePortal, Blocks.NETHER_PORTAL);
         } else if (!mod.getBlockScanner().anyFound(standablePortal, Blocks.NETHER_PORTAL)) {
            this.setDebugState("Making new nether portal.");
            return (Task)(WorldHelper.getCurrentDimension(this.controller) == Dimension.OVERWORLD
               ? new ConstructNetherPortalBucketTask()
               : new ConstructNetherPortalObsidianTask());
         } else {
            this.setDebugState("Getting our portal");
            return this.getPortalTask;
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   public boolean isFinished() {
      return WorldHelper.getCurrentDimension(this.controller) == this.targetDimension;
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof EnterNetherPortalTask task)
         ? false
         : Objects.equals(task.getPortalTask, this.getPortalTask) && Objects.equals(task.targetDimension, this.targetDimension);
   }

   @Override
   protected String toDebugString() {
      return "Entering nether portal";
   }
}
