package com.player2.playerengine.tasks.movement;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.chains.UserTaskChain;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.tasks.entity.KillEntitiesTask;
import com.player2.playerengine.tasks.base.ITaskRequiresGrounded;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.util.progresscheck.MovementProgressChecker;
import com.player2.playerengine.util.slots.Slot;
import com.player2.playerengine.util.time.TimerGame;
import com.player2.playerengine.automaton.api.utils.input.Input;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.phys.Vec3;

public class TimeoutWanderTask extends Task implements ITaskRequiresGrounded {
   private final MovementProgressChecker stuckCheck = new MovementProgressChecker();
   private final float distanceToWander;
   private final MovementProgressChecker progressChecker = new MovementProgressChecker();
   private final boolean increaseRange;
   private final TimerGame timer = new TimerGame(60.0);
   Block[] annoyingBlocks = new Block[]{
      Blocks.VINE,
      Blocks.NETHER_SPROUTS,
      Blocks.CAVE_VINES,
      Blocks.CAVE_VINES_PLANT,
      Blocks.TWISTING_VINES,
      Blocks.TWISTING_VINES_PLANT,
      Blocks.WEEPING_VINES_PLANT,
      Blocks.LADDER,
      Blocks.BIG_DRIPLEAF,
      Blocks.BIG_DRIPLEAF_STEM,
      Blocks.SMALL_DRIPLEAF,
      Blocks.TALL_GRASS,
      Blocks.GRASS_BLOCK,
      Blocks.SWEET_BERRY_BUSH
   };
   private Vec3 origin;
   private boolean forceExplore;
   private Task unstuckTask = null;
   private int failCounter;
   private double wanderDistanceExtension;

   /**
    * Consecutive stalled progress checks (under 0.1 blocks moved in 6 s) before the user's command is
    * given up on — 10 is about a minute of standing still.
    *
    * <p>⚠️ An unbounded wander ({@link #TimeoutWanderTask()}, what most resource tasks fall back to
    * when nothing they want is loaded) can never finish: {@link #isFinished()} returns false for an
    * infinite distance before it ever looks at {@link #failCounter}. Measured 2026-09-25: "get wool 3"
    * in a snow biome with no sheep logged "Failed exploring." every 6 s for three minutes and would
    * have gone on forever, with the model told nothing and the owner watching a companion stand still.
    * Finishing only the wander would not help either — the parent hands back a new one next tick.
    */
   private static final int STALLS_BEFORE_GIVING_UP = 10;
   /** A gap this long between stalls means it moved in between, so the streak starts over. */
   private static final long STALL_STREAK_GAP_MS = 15_000L;
   private int stallStreak;
   private long lastStallMs;

   public TimeoutWanderTask(float distanceToWander, boolean increaseRange) {
      this.distanceToWander = distanceToWander;
      this.increaseRange = increaseRange;
      this.forceExplore = false;
   }

   public TimeoutWanderTask(float distanceToWander) {
      this(distanceToWander, false);
   }

   public TimeoutWanderTask() {
      this(Float.POSITIVE_INFINITY, false);
   }

   public TimeoutWanderTask(boolean forceExplore) {
      this();
      this.forceExplore = forceExplore;
   }

   private static BlockPos[] generateSides(BlockPos pos) {
      return new BlockPos[]{
         pos.offset(1, 0, 0),
         pos.offset(-1, 0, 0),
         pos.offset(0, 0, 1),
         pos.offset(0, 0, -1),
         pos.offset(1, 0, -1),
         pos.offset(1, 0, 1),
         pos.offset(-1, 0, -1),
         pos.offset(-1, 0, 1)
      };
   }

   private boolean isAnnoying(PlayerEngineController mod, BlockPos pos) {
      Block[] arrayOfBlock = this.annoyingBlocks;
      int i = arrayOfBlock.length;
      byte b = 0;
      if (b >= i) {
         return false;
      } else {
         Block AnnoyingBlocks = arrayOfBlock[b];
         return mod.getWorld().getBlockState(pos).getBlock() == AnnoyingBlocks
            || mod.getWorld().getBlockState(pos).getBlock() instanceof DoorBlock
            || mod.getWorld().getBlockState(pos).getBlock() instanceof FenceBlock
            || mod.getWorld().getBlockState(pos).getBlock() instanceof FenceGateBlock
            || mod.getWorld().getBlockState(pos).getBlock() instanceof FlowerBlock;
      }
   }

   public void resetWander() {
      this.wanderDistanceExtension = 0.0;
   }

   private BlockPos stuckInBlock(PlayerEngineController mod) {
      BlockPos p = mod.getPlayer().blockPosition();
      if (this.isAnnoying(mod, p)) {
         return p;
      } else if (this.isAnnoying(mod, p.above())) {
         return p.above();
      } else {
         BlockPos[] toCheck = generateSides(p);

         for (BlockPos check : toCheck) {
            if (this.isAnnoying(mod, check)) {
               return check;
            }
         }

         BlockPos[] toCheckHigh = generateSides(p.above());

         for (BlockPos checkx : toCheckHigh) {
            if (this.isAnnoying(mod, checkx)) {
               return checkx;
            }
         }

         return null;
      }
   }

   private Task getFenceUnstuckTask() {
      return new SafeRandomShimmyTask();
   }

   @Override
   protected void onStart() {
      PlayerEngineController mod = this.controller;
      this.timer.reset();
      mod.getBaritone().getPathingBehavior().forceCancel();
      this.origin = mod.getPlayer().position();
      this.progressChecker.reset();
      this.stuckCheck.reset();
      this.failCounter = 0;
      ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot(this.controller);
      if (!cursorStack.isEmpty()) {
         Optional<Slot> moveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursorStack, false);
         moveTo.ifPresent(slot -> mod.getSlotHandler().clickSlot(slot, 0, ClickType.PICKUP));
         if (ItemHelper.canThrowAwayStack(mod, cursorStack)) {
            mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
         }

         Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
         garbage.ifPresent(slot -> mod.getSlotHandler().clickSlot(slot, 0, ClickType.PICKUP));
         mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
      }
   }

   @Override
   protected Task onTick() {
      PlayerEngineController mod = this.controller;
      if (mod.getBaritone().getPathingBehavior().isPathing()) {
         this.progressChecker.reset();
      }

      if (WorldHelper.isInNetherPortal(this.controller)) {
         if (!mod.getBaritone().getPathingBehavior().isPathing()) {
            this.setDebugState("Getting out from nether portal");
            mod.getInputControls().hold(Input.SNEAK);
            mod.getInputControls().hold(Input.MOVE_FORWARD);
            return null;
         }

         mod.getInputControls().release(Input.SNEAK);
         mod.getInputControls().release(Input.MOVE_BACK);
         mod.getInputControls().release(Input.MOVE_FORWARD);
      } else if (mod.getBaritone().getPathingBehavior().isPathing()) {
         mod.getInputControls().release(Input.SNEAK);
         mod.getInputControls().release(Input.MOVE_BACK);
         mod.getInputControls().release(Input.MOVE_FORWARD);
      }

      if (this.unstuckTask != null && this.unstuckTask.isActive() && !this.unstuckTask.isFinished() && this.stuckInBlock(mod) != null) {
         this.setDebugState("Getting unstuck from block.");
         this.stuckCheck.reset();
         mod.getBaritone().getCustomGoalProcess().onLostControl();
         mod.getBaritone().getExploreProcess().onLostControl();
         return this.unstuckTask;
      } else {
         if (!this.progressChecker.check(mod) || !this.stuckCheck.check(mod)) {
            for (Entity CloseEntities : mod.getEntityTracker().getCloseEntities()) {
               if (CloseEntities instanceof Mob && CloseEntities.position().closerThan(mod.getPlayer().position(), 1.0) && CloseEntities != mod.getEntity()) {
                  this.setDebugState("Killing annoying entity.");
                  return new KillEntitiesTask(CloseEntities.getClass());
               }
            }

            BlockPos blockStuck = this.stuckInBlock(mod);
            if (blockStuck != null) {
               this.failCounter++;
               this.unstuckTask = this.getFenceUnstuckTask();
               return this.unstuckTask;
            }

            this.stuckCheck.reset();
         }

         this.setDebugState("Exploring.");
         switch (WorldHelper.getCurrentDimension(this.controller)) {
            case END:
               if (this.timer.getDuration() >= 30.0) {
                  this.timer.reset();
               }
               break;
            case OVERWORLD:
            case NETHER:
               if (this.timer.getDuration() >= 30.0) {
               }

               if (this.timer.elapsed()) {
                  this.timer.reset();
               }
         }

         if (!mod.getBaritone().getExploreProcess().isActive()) {
            mod.getBaritone().getExploreProcess().explore((int)this.origin.x(), (int)this.origin.z());
         }

         if (!this.progressChecker.check(mod)) {
            this.progressChecker.reset();
            if (!this.forceExplore) {
               this.failCounter++;
               Debug.logMessage("Failed exploring.");
               if (this.progressChecker.lastBreakingBlock != null) {
               }
            }

            // Counted for forceExplore too: it skips the log line above, not the being stuck.
            this.giveUpIfStalled(mod);
         }

         return null;
      }
   }

   /**
    * Fail the user's whole command once this wander has stood still for too long.
    *
    * <p>The ROOT task is failed, not this one: the chain sees it stopped on its next tick and runs
    * the command's finish handler, which is what reports the recorded failure to the model. Only
    * when the wander is part of the user's own command — a food or defence chain's wander is not
    * ours to cancel, and the idle task has nobody waiting on it.
    */
   private void giveUpIfStalled(PlayerEngineController mod) {
      long now = System.currentTimeMillis();
      this.stallStreak = now - this.lastStallMs > STALL_STREAK_GAP_MS ? 1 : this.stallStreak + 1;
      this.lastStallMs = now;
      if (this.stallStreak < STALLS_BEFORE_GIVING_UP) {
         return;
      }

      UserTaskChain chain = mod.getUserTaskChain();
      // Identity, not contains(): Task.equals is isEqual, and every unbounded wander equals every other.
      if (mod.getTaskRunner().getCurrentTaskChain() != chain
            || chain.isRunningIdleTask()
            || chain.getTasks().isEmpty()
            || chain.getTasks().stream().noneMatch(t -> t == this)) {
         return;
      }

      Task root = chain.getTasks().get(0);
      this.stallStreak = 0;
      mod.logAgentNotice(
         "Gave up on " + root + ": stood still for about a minute searching for what it needs, and "
            + "nothing reachable turned up nearby. Running the same command again here will get stuck "
            + "the same way — check nearby chests with `chests` and take it with `withdraw`, use what "
            + "is already in the inventory, try a different material, or ask the owner for the items.",
         "I gave up — I searched for a minute and couldn't find what I needed anywhere I could reach.");
      root.fail("stuck exploring for " + STALLS_BEFORE_GIVING_UP + " progress checks");
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBaritone().getPathingBehavior().forceCancel();
      if (this.isFinished() && this.increaseRange) {
         this.wanderDistanceExtension = this.wanderDistanceExtension + this.distanceToWander;
         Debug.logMessage("Increased wander range");
      }
   }

   @Override
   public boolean isFinished() {
      if (Float.isInfinite(this.distanceToWander)) {
         return false;
      } else if (this.failCounter > 10) {
         return true;
      } else {
         LivingEntity player = this.controller.getPlayer();
         if (player != null && player.position() != null && (player.onGround() || player.isInWater())) {
            double sqDist = player.position().distanceToSqr(this.origin);
            double toWander = this.distanceToWander + this.wanderDistanceExtension;
            return sqDist > toWander * toWander;
         } else {
            return false;
         }
      }
   }

   @Override
   protected boolean isEqual(Task other) {
      if (other instanceof TimeoutWanderTask task) {
         return !Float.isInfinite(task.distanceToWander) && !Float.isInfinite(this.distanceToWander)
            ? Math.abs(task.distanceToWander - this.distanceToWander) < 0.5F
            : Float.isInfinite(task.distanceToWander) == Float.isInfinite(this.distanceToWander);
      } else {
         return false;
      }
   }

   @Override
   protected String toDebugString() {
      return "Wander for " + this.distanceToWander + this.wanderDistanceExtension + " blocks";
   }
}
