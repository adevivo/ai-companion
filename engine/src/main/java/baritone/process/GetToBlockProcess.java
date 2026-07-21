package baritone.process;

import baritone.PlayerEngine;
import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalRunAway;
import baritone.api.pathing.goals.GoalTwoBlocks;
import baritone.api.process.IGetToBlockProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class GetToBlockProcess extends BaritoneProcessHelper implements IGetToBlockProcess {
   private BlockOptionalMeta gettingTo;
   private List<BlockPos> knownLocations;
   private List<BlockPos> blacklist;
   private BlockPos start;
   private int tickCount = 0;
   private int arrivalTickCount = 0;

   public GetToBlockProcess(Baritone baritone) {
      super(baritone);
   }

   @Override
   public void getToBlock(BlockOptionalMeta block) {
      this.onLostControl();
      this.gettingTo = block;
      this.start = this.ctx.feetPos();
      this.blacklist = new ArrayList<>();
      this.arrivalTickCount = 0;
      this.rescan(new ArrayList<>(), new CalculationContext(this.baritone));
   }

   @Override
   public boolean isActive() {
      return this.gettingTo != null;
   }

   @Override
   public synchronized PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      if (this.knownLocations == null) {
         this.rescan(new ArrayList<>(), new CalculationContext(this.baritone));
      }

      if (this.knownLocations.isEmpty()) {
         if (this.baritone.settings().exploreForBlocks.get() && !calcFailed) {
            return new PathingCommand(new GoalRunAway(1.0, this.start) {
               @Override
               public boolean isInGoal(int x, int y, int z) {
                  return false;
               }

               @Override
               public double heuristic() {
                  return Double.NEGATIVE_INFINITY;
               }
            }, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
         } else {
            this.logDirect("No known locations of " + this.gettingTo + ", canceling GetToBlock");
            if (isSafeToCancel) {
               this.onLostControl();
            }

            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
         }
      } else {
         Goal goal = new GoalComposite(this.knownLocations.stream().map(this::createGoal).toArray(Goal[]::new));
         if (calcFailed) {
            if (this.baritone.settings().blacklistClosestOnFailure.get()) {
               this.logDirect("Unable to find any path to " + this.gettingTo + ", blacklisting presumably unreachable closest instances...");
               this.blacklistClosest();
               return this.onTick(false, isSafeToCancel);
            } else {
               this.logDirect("Unable to find any path to " + this.gettingTo + ", canceling GetToBlock");
               if (isSafeToCancel) {
                  this.onLostControl();
               }

               return new PathingCommand(goal, PathingCommandType.CANCEL_AND_SET_GOAL);
            }
         } else {
            int mineGoalUpdateInterval = this.baritone.settings().mineGoalUpdateInterval.get();
            if (mineGoalUpdateInterval != 0 && this.tickCount++ % mineGoalUpdateInterval == 0) {
               List<BlockPos> current = new ArrayList<>(this.knownLocations);
               CalculationContext context = new CalculationContext(this.baritone, true);
               PlayerEngine.getExecutor().execute(() -> this.rescan(current, context));
            }

            if (goal.isInGoal(this.ctx.feetPos()) && goal.isInGoal(this.baritone.getPathingBehavior().pathStart()) && isSafeToCancel) {
               if (!this.rightClickOnArrival(this.gettingTo.getBlock())) {
                  this.onLostControl();
                  return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
               }

               if (this.rightClick()) {
                  this.onLostControl();
                  return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
               }
            }

            return new PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
         }
      }
   }

   @Override
   public synchronized boolean blacklistClosest() {
      List<BlockPos> newBlacklist = new ArrayList<>();
      this.knownLocations.stream().min(Comparator.comparingDouble(this.ctx.feetPos()::distSqr)).ifPresent(newBlacklist::add);

      label32:
      while (true) {
         for (BlockPos known : this.knownLocations) {
            for (BlockPos blacklist : newBlacklist) {
               if (this.areAdjacent(known, blacklist)) {
                  newBlacklist.add(known);
                  this.knownLocations.remove(known);
                  continue label32;
               }
            }
         }

         switch (newBlacklist.size()) {
            default:
               this.baritone.logDebug("Blacklisting unreachable locations " + newBlacklist);
               this.blacklist.addAll(newBlacklist);
               return !newBlacklist.isEmpty();
         }
      }
   }

   private boolean areAdjacent(BlockPos posA, BlockPos posB) {
      int diffX = Math.abs(posA.getX() - posB.getX());
      int diffY = Math.abs(posA.getY() - posB.getY());
      int diffZ = Math.abs(posA.getZ() - posB.getZ());
      return diffX + diffY + diffZ == 1;
   }

   @Override
   public synchronized void onLostControl() {
      this.gettingTo = null;
      this.knownLocations = null;
      this.start = null;
      this.blacklist = null;
      this.baritone.getInputOverrideHandler().clearAllKeys();
   }

   @Override
   public String displayName0() {
      return this.knownLocations.isEmpty()
         ? "Exploring randomly to find " + this.gettingTo + ", no known locations"
         : "Get To " + this.gettingTo + ", " + this.knownLocations.size() + " known locations";
   }

   private synchronized void rescan(List<BlockPos> known, CalculationContext context) {
      List<BlockPos> positions = MineProcess.searchWorld(
         context, new BlockOptionalMetaLookup(this.gettingTo), 64, known, this.blacklist, Collections.emptyList()
      );
      positions.removeIf(this.blacklist::contains);
      this.knownLocations = positions;
   }

   private Goal createGoal(BlockPos pos) {
      if (this.walkIntoInsteadOfAdjacent(this.gettingTo.getBlock())) {
         return new GoalTwoBlocks(pos);
      } else {
         return (Goal)(this.blockOnTopMustBeRemoved(this.gettingTo.getBlock()) && MovementHelper.isBlockNormalCube(this.baritone.bsi.get0(pos.above()))
            ? new GoalBlock(pos.above())
            : new GoalGetToBlock(pos));
      }
   }

   private boolean rightClick() {
      for (BlockPos pos : this.knownLocations) {
         Optional<Rotation> reachable = RotationUtils.reachable(this.ctx.entity(), pos, this.ctx.playerController().getBlockReachDistance());
         if (reachable.isPresent()) {
            this.baritone.getLookBehavior().updateTarget(reachable.get(), true);
            if (this.knownLocations.contains(this.ctx.getSelectedBlock().orElse(null))) {
               this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
            }

            if (this.arrivalTickCount++ > 20) {
               this.logDirect("Right click timed out");
               return true;
            }

            return false;
         }
      }

      this.logDirect("Arrived but failed to right click open");
      return true;
   }

   private boolean walkIntoInsteadOfAdjacent(Block block) {
      return !this.baritone.settings().enterPortal.get() ? false : block == Blocks.NETHER_PORTAL;
   }

   private boolean rightClickOnArrival(Block block) {
      return !this.baritone.settings().rightClickContainerOnArrival.get()
         ? false
         : block == Blocks.CRAFTING_TABLE || block == Blocks.FURNACE || block == Blocks.ENDER_CHEST || block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST;
   }

   private boolean blockOnTopMustBeRemoved(Block block) {
      return !this.rightClickOnArrival(block) ? false : block == Blocks.ENDER_CHEST || block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST;
   }
}
