package baritone.pathing.path;

import baritone.PlayerEngine;
import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.movement.IMovement;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IEntityContext;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.VecUtils;
import baritone.api.utils.input.Input;
import baritone.behavior.PathingBehavior;
import baritone.pathing.calc.AbstractNodeCostSearch;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.movements.MovementAscend;
import baritone.pathing.movement.movements.MovementDescend;
import baritone.pathing.movement.movements.MovementDiagonal;
import baritone.pathing.movement.movements.MovementFall;
import baritone.pathing.movement.movements.MovementTraverse;
import baritone.utils.BlockStateInterface;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Tuple;
import net.minecraft.world.phys.Vec3;

public class PathExecutor implements IPathExecutor {
   private static final double MAX_MAX_DIST_FROM_PATH = 3.0;
   private static final double MAX_DIST_FROM_PATH = 2.0;
   private static final double MAX_TICKS_AWAY = 200.0;
   private final IPath path;
   private int pathPosition;
   private int ticksAway;
   private int ticksOnCurrent;
   private Double currentMovementOriginalCostEstimate;
   private Integer costEstimateIndex;
   private boolean failed;
   private boolean recalcBP = true;
   private HashSet<BlockPos> toBreak = new HashSet<>();
   private HashSet<BlockPos> toPlace = new HashSet<>();
   private HashSet<BlockPos> toWalkInto = new HashSet<>();
   private final PathingBehavior behavior;
   private final IEntityContext ctx;
   private boolean sprintNextTick;

   public PathExecutor(PathingBehavior behavior, IPath path) {
      this.behavior = behavior;
      this.ctx = behavior.ctx;
      this.path = path;
      this.pathPosition = 0;
   }

   public void logDebug(String message) {
      this.ctx.logDebug(message);
   }

   public boolean onTick() {
      if (this.pathPosition == this.path.length() - 1) {
         this.pathPosition++;
      }

      if (this.pathPosition >= this.path.length()) {
         return true;
      } else {
         Movement movement = (Movement)this.path.movements().get(this.pathPosition);
         BetterBlockPos whereAmI = this.ctx.feetPos();
         if (!movement.getValidPositions().contains(whereAmI)) {
            for (int i = this.pathPosition + 3; i < this.path.length() - 1; i++) {
               if (((Movement)this.path.movements().get(i)).getValidPositions().contains(whereAmI)) {
                  if (i - this.pathPosition > 2) {
                     this.logDebug("Skipping forward " + (i - this.pathPosition) + " steps, to " + i);
                  }

                  this.pathPosition = i - 1;
                  this.onChangeInPathPosition();
                  this.onTick();
                  return false;
               }
            }
         }

         Tuple<Double, BlockPos> status = this.closestPathPos(this.path);
         if (this.possiblyOffPath(status, 2.0)) {
            this.ticksAway++;
            PlayerEngine.LOGGER.warn("FAR AWAY FROM PATH FOR " + this.ticksAway + " TICKS. Current distance: " + status.getA() + ". Threshold: 2.0");
            if (this.ticksAway > 200.0) {
               this.logDebug("Too far away from path for too long, cancelling path");
               this.cancel();
               return false;
            }
         } else {
            this.ticksAway = 0;
         }

         if (this.possiblyOffPath(status, 3.0)) {
            this.logDebug("too far from path");
            this.cancel();
            return false;
         } else {
            BlockStateInterface bsi = new BlockStateInterface(this.ctx);

            for (int ix = this.pathPosition - 10; ix < this.pathPosition + 10; ix++) {
               if (ix >= 0 && ix < this.path.movements().size()) {
                  Movement m = (Movement)this.path.movements().get(ix);
                  List<BlockPos> prevBreak = m.toBreak(bsi);
                  List<BlockPos> prevPlace = m.toPlace(bsi);
                  List<BlockPos> prevWalkInto = m.toWalkInto(bsi);
                  m.resetBlockCache();
                  if (!prevBreak.equals(m.toBreak(bsi))) {
                     this.recalcBP = true;
                  }

                  if (!prevPlace.equals(m.toPlace(bsi))) {
                     this.recalcBP = true;
                  }

                  if (!prevWalkInto.equals(m.toWalkInto(bsi))) {
                     this.recalcBP = true;
                  }
               }
            }

            if (this.recalcBP) {
               HashSet<BlockPos> newBreak = new HashSet<>();
               HashSet<BlockPos> newPlace = new HashSet<>();
               HashSet<BlockPos> newWalkInto = new HashSet<>();

               for (int ixx = this.pathPosition; ixx < this.path.movements().size(); ixx++) {
                  Movement mx = (Movement)this.path.movements().get(ixx);
                  newBreak.addAll(mx.toBreak(bsi));
                  newPlace.addAll(mx.toPlace(bsi));
                  newWalkInto.addAll(mx.toWalkInto(bsi));
               }

               this.toBreak = newBreak;
               this.toPlace = newPlace;
               this.toWalkInto = newWalkInto;
               this.recalcBP = false;
            }

            Baritone baritone = this.behavior.baritone;
            if (this.pathPosition < this.path.movements().size() - 1) {
               IMovement next = this.path.movements().get(this.pathPosition + 1);
               if (!baritone.bsi.worldContainsLoadedChunk(next.getDest().x, next.getDest().z)) {
                  this.logDebug("Pausing since destination is at edge of loaded chunks");
                  this.clearKeys();
                  return true;
               }
            }

            boolean canCancel = movement.safeToCancel();
            if (this.costEstimateIndex == null || this.costEstimateIndex != this.pathPosition) {
               this.costEstimateIndex = this.pathPosition;
               this.currentMovementOriginalCostEstimate = movement.getCost();

               for (int ixx = 1; ixx < baritone.settings().costVerificationLookahead.get() && this.pathPosition + ixx < this.path.length() - 1; ixx++) {
                  if (((Movement)this.path.movements().get(this.pathPosition + ixx)).calculateCost(this.behavior.secretInternalGetCalculationContext())
                        >= 1000000.0
                     && canCancel) {
                     this.logDebug("Something has changed in the world and a future movement has become impossible. Cancelling.");
                     this.cancel();
                     return true;
                  }
               }
            }

            double currentCost = movement.recalculateCost(this.behavior.secretInternalGetCalculationContext());
            if (currentCost >= 1000000.0 && canCancel) {
               this.logDebug("Something has changed in the world and this movement has become impossible. Cancelling.");
               this.cancel();
               return true;
            } else if (!movement.calculatedWhileLoaded()
               && currentCost - this.currentMovementOriginalCostEstimate > baritone.settings().maxCostIncrease.get()
               && canCancel) {
               this.logDebug("Original cost " + this.currentMovementOriginalCostEstimate + " current cost " + currentCost + ". Cancelling.");
               this.cancel();
               return true;
            } else if (this.shouldPause()) {
               this.logDebug("Pausing since current best path is a backtrack");
               this.clearKeys();
               return true;
            } else {
               MovementStatus movementStatus = movement.update();
               if (movementStatus == MovementStatus.UNREACHABLE || movementStatus == MovementStatus.FAILED) {
                  this.logDebug("Movement returns status " + movementStatus);
                  this.cancel();
                  return true;
               } else if (movementStatus == MovementStatus.SUCCESS) {
                  this.pathPosition++;
                  this.onChangeInPathPosition();
                  this.onTick();
                  return true;
               } else {
                  this.ctx.entity().setSprinting(this.shouldSprintNextTick());
                  this.ticksOnCurrent++;
                  if (this.ticksOnCurrent > this.currentMovementOriginalCostEstimate + baritone.settings().movementTimeoutTicks.get().intValue()) {
                     this.logDebug(
                        "This movement has taken too long ("
                           + this.ticksOnCurrent
                           + " ticks, expected "
                           + this.currentMovementOriginalCostEstimate
                           + "). Cancelling."
                     );
                     this.cancel();
                     return true;
                  } else {
                     return canCancel;
                  }
               }
            }
         }
      }
   }

   private Tuple<Double, BlockPos> closestPathPos(IPath path) {
      double best = -1.0;
      BlockPos bestPos = null;

      for (IMovement movement : path.movements()) {
         for (BlockPos pos : ((Movement)movement).getValidPositions()) {
            double dist = VecUtils.entityDistanceToCenter(this.ctx.entity(), pos);
            if (dist < best || best == -1.0) {
               best = dist;
               bestPos = pos;
            }
         }
      }

      return new Tuple(best, bestPos);
   }

   private boolean shouldPause() {
      Optional<AbstractNodeCostSearch> current = this.behavior.getInProgress();
      if (!current.isPresent()) {
         return false;
      } else if (!this.ctx.entity().onGround()) {
         return false;
      } else if (!MovementHelper.canWalkOn(this.ctx, this.ctx.feetPos().down())) {
         return false;
      } else if (!MovementHelper.canWalkThrough(this.ctx, this.ctx.feetPos()) || !MovementHelper.canWalkThrough(this.ctx, this.ctx.feetPos().up())) {
         return false;
      } else if (!this.path.movements().get(this.pathPosition).safeToCancel()) {
         return false;
      } else {
         Optional<IPath> currentBest = current.get().bestPathSoFar();
         if (!currentBest.isPresent()) {
            return false;
         } else {
            List<BetterBlockPos> positions = currentBest.get().positions();
            if (positions.size() < 3) {
               return false;
            } else {
               positions = positions.subList(1, positions.size());
               return positions.contains(this.ctx.feetPos());
            }
         }
      }
   }

   private boolean possiblyOffPath(Tuple<Double, BlockPos> status, double leniency) {
      double distanceFromPath = (Double)status.getA();
      if (distanceFromPath > leniency) {
         if (this.path.movements().get(this.pathPosition) instanceof MovementFall) {
            BlockPos fallDest = this.path.positions().get(this.pathPosition + 1);
            return VecUtils.entityFlatDistanceToCenter(this.ctx.entity(), fallDest) >= leniency;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean snipsnapifpossible() {
      if (!this.ctx.entity().onGround() && this.ctx.world().getFluidState(this.ctx.feetPos()).isEmpty()) {
         return false;
      } else if (this.ctx.entity().getDeltaMovement().y < -0.1) {
         return false;
      } else {
         int index = this.path.positions().indexOf(this.ctx.feetPos());
         if (index == -1) {
            return false;
         } else {
            this.pathPosition = index;
            this.clearKeys();
            return true;
         }
      }
   }

   private boolean shouldSprintNextTick() {
      boolean requested = this.behavior.baritone.getInputOverrideHandler().isInputForcedDown(Input.SPRINT);
      this.behavior.baritone.getInputOverrideHandler().setInputForceState(Input.SPRINT, false);
      if (!(new CalculationContext(this.behavior.baritone)).canSprint) {
         return false;
      } else {
         IMovement current = this.path.movements().get(this.pathPosition);
         if (current instanceof MovementTraverse && this.pathPosition < this.path.length() - 3) {
            IMovement next = this.path.movements().get(this.pathPosition + 1);
            if (next instanceof MovementAscend
               && this.behavior.baritone.settings().sprintAscends.get()
               && sprintableAscend(this.ctx, (MovementTraverse)current, (MovementAscend)next, this.path.movements().get(this.pathPosition + 2))) {
               if (skipNow(this.ctx, current)) {
                  this.logDebug("Skipping traverse to straight ascend");
                  this.pathPosition++;
                  this.onChangeInPathPosition();
                  this.onTick();
                  this.behavior.baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
                  return true;
               }

               this.logDebug("Too far to the side to safely sprint ascend");
            }
         }

         if (current instanceof MovementDiagonal
            && this.ctx.entity().isUnderWater()
            && this.ctx.world().getBlockState(this.ctx.feetPos().up()).getFluidState().isEmpty()) {
            return false;
         } else if (requested) {
            return true;
         } else {
            if (current instanceof MovementDescend) {
               if (((MovementDescend)current).safeMode() && !((MovementDescend)current).skipToAscend()) {
                  this.logDebug("Sprinting would be unsafe");
                  return false;
               }

               if (this.pathPosition < this.path.length() - 2) {
                  IMovement next = this.path.movements().get(this.pathPosition + 1);
                  if (next instanceof MovementAscend && current.getDirection().above().equals(next.getDirection().below())) {
                     this.pathPosition++;
                     this.onChangeInPathPosition();
                     this.onTick();
                     this.logDebug("Skipping descend to straight ascend");
                     return true;
                  }

                  if (canSprintFromDescendInto(this.ctx, current, next, this.behavior.baritone.settings())) {
                     if (this.ctx.feetPos().equals(current.getDest())) {
                        this.pathPosition++;
                        this.onChangeInPathPosition();
                        this.onTick();
                     }

                     return true;
                  }
               }
            }

            if (current instanceof MovementAscend && this.pathPosition != 0) {
               IMovement prev = this.path.movements().get(this.pathPosition - 1);
               if (prev instanceof MovementDescend && prev.getDirection().above().equals(current.getDirection().below())) {
                  BlockPos center = current.getSrc().up();
                  if (this.ctx.entity().getY() >= center.getY() - 0.07) {
                     this.behavior.baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
                     return true;
                  }
               }

               if (this.pathPosition < this.path.length() - 2
                  && prev instanceof MovementTraverse
                  && sprintableAscend(this.ctx, (MovementTraverse)prev, (MovementAscend)current, this.path.movements().get(this.pathPosition + 1))) {
                  return true;
               }

               if (this.pathPosition < this.path.length() - 1
                  && (prev.getDirection().getX() != 0 || prev.getDirection().getZ() != 0)
                  && this.ctx.entity().isUnderWater()) {
                  return true;
               }
            }

            if (current instanceof MovementTraverse && this.ctx.entity().isUnderWater() && this.pathPosition != 0) {
               IMovement prevx = this.path.movements().get(this.pathPosition - 1);
               return (prevx.getDirection().getX() != 0 || prevx.getDirection().getZ() != 0)
                  && !this.ctx.world().getBlockState(this.ctx.feetPos().up()).getFluidState().isEmpty();
            } else {
               if (current instanceof MovementFall) {
                  Tuple<Vec3, BlockPos> data = this.overrideFall((MovementFall)current);
                  if (data != null) {
                     BetterBlockPos fallDest = new BetterBlockPos((BlockPos)data.getB());
                     if (!this.path.positions().contains(fallDest)) {
                        throw new IllegalStateException();
                     }

                     if (this.ctx.feetPos().equals(fallDest)) {
                        this.pathPosition = this.path.positions().indexOf(fallDest);
                        this.onChangeInPathPosition();
                        this.onTick();
                        return true;
                     }

                     this.clearKeys();
                     this.behavior
                        .baritone
                        .getLookBehavior()
                        .updateTarget(RotationUtils.calcRotationFromVec3d(this.ctx.headPos(), (Vec3)data.getA(), this.ctx.entityRotations()), false);
                     this.behavior.baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
                     return true;
                  }
               }

               return false;
            }
         }
      }
   }

   private Tuple<Vec3, BlockPos> overrideFall(MovementFall movement) {
      Vec3i dir = movement.getDirection();
      if (dir.getY() < -3) {
         return null;
      } else if (!movement.toBreakCached.isEmpty()) {
         return null;
      } else {
         Vec3i flatDir = new Vec3i(dir.getX(), 0, dir.getZ());

         int i;
         label49:
         for (i = this.pathPosition + 1; i < this.path.length() - 1 && i < this.pathPosition + 3; i++) {
            IMovement next = this.path.movements().get(i);
            if (!(next instanceof MovementTraverse) || !flatDir.equals(next.getDirection())) {
               break;
            }

            for (int y = next.getDest().y; y <= movement.getSrc().y + 1; y++) {
               BlockPos chk = new BlockPos(next.getDest().x, y, next.getDest().z);
               if (!MovementHelper.fullyPassable(this.ctx, chk)) {
                  break label49;
               }
            }

            if (!MovementHelper.canWalkOn(this.ctx, next.getDest().down())) {
               break;
            }
         }

         if (--i == this.pathPosition) {
            return null;
         } else {
            double len = i - this.pathPosition - 0.4;
            return new Tuple(
               new Vec3(flatDir.getX() * len + movement.getDest().x + 0.5, movement.getDest().y, flatDir.getZ() * len + movement.getDest().z + 0.5),
               movement.getDest().offset(flatDir.getX() * (i - this.pathPosition), 0, flatDir.getZ() * (i - this.pathPosition))
            );
         }
      }
   }

   private static boolean skipNow(IEntityContext ctx, IMovement current) {
      double offTarget = Math.abs(current.getDirection().getX() * (current.getSrc().z + 0.5 - ctx.entity().getZ()))
         + Math.abs(current.getDirection().getZ() * (current.getSrc().x + 0.5 - ctx.entity().getX()));
      if (offTarget > 0.1) {
         return false;
      } else {
         BlockPos headBonk = current.getSrc().subtract(current.getDirection()).above(2);
         if (MovementHelper.fullyPassable(ctx, headBonk)) {
            return true;
         } else {
            double flatDist = Math.abs(current.getDirection().getX() * (headBonk.getX() + 0.5 - ctx.entity().getX()))
               + Math.abs(current.getDirection().getZ() * (headBonk.getZ() + 0.5 - ctx.entity().getZ()));
            return flatDist > 0.8;
         }
      }
   }

   private static boolean sprintableAscend(IEntityContext ctx, MovementTraverse current, MovementAscend next, IMovement nextnext) {
      if (!current.getDirection().equals(next.getDirection().below())) {
         return false;
      } else if (nextnext.getDirection().getX() == next.getDirection().getX() && nextnext.getDirection().getZ() == next.getDirection().getZ()) {
         if (!MovementHelper.canWalkOn(ctx, current.getDest().down())) {
            return false;
         } else if (!MovementHelper.canWalkOn(ctx, next.getDest().down())) {
            return false;
         } else if (!next.toBreakCached.isEmpty()) {
            return false;
         } else {
            for (int x = 0; x < 2; x++) {
               for (int y = 0; y < 3; y++) {
                  BlockPos chk = current.getSrc().up(y);
                  if (x == 1) {
                     chk = chk.offset(current.getDirection());
                  }

                  if (!MovementHelper.fullyPassable(ctx, chk)) {
                     return false;
                  }
               }
            }

            return MovementHelper.avoidWalkingInto(ctx.world().getBlockState(current.getSrc().up(3)))
               ? false
               : !MovementHelper.avoidWalkingInto(ctx.world().getBlockState(next.getDest().up(2)));
         }
      } else {
         return false;
      }
   }

   private static boolean canSprintFromDescendInto(IEntityContext ctx, IMovement current, IMovement next, Settings settings) {
      if (next instanceof MovementDescend && next.getDirection().equals(current.getDirection())) {
         return true;
      } else if (!MovementHelper.canWalkOn(ctx, current.getDest().offset(current.getDirection()))) {
         return false;
      } else {
         return next instanceof MovementTraverse && next.getDirection().below().equals(current.getDirection())
            ? true
            : next instanceof MovementDiagonal && settings.allowOvershootDiagonalDescend.get();
      }
   }

   private void onChangeInPathPosition() {
      this.clearKeys();
      this.ticksOnCurrent = 0;
   }

   private void clearKeys() {
      this.behavior.baritone.getInputOverrideHandler().clearAllKeys();
   }

   private void cancel() {
      this.clearKeys();
      this.behavior.baritone.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
      this.pathPosition = this.path.length() + 3;
      this.failed = true;
   }

   @Override
   public int getPosition() {
      return this.pathPosition;
   }

   public PathExecutor trySplice(PathExecutor next) {
      return next == null ? this.cutIfTooLong() : SplicedPath.trySplice(this.path, next.path, false).map(path -> {
         if (!path.getDest().equals(next.getPath().getDest())) {
            throw new IllegalStateException();
         } else {
            PathExecutor ret = new PathExecutor(this.behavior, path);
            ret.pathPosition = this.pathPosition;
            ret.currentMovementOriginalCostEstimate = this.currentMovementOriginalCostEstimate;
            ret.costEstimateIndex = this.costEstimateIndex;
            ret.ticksOnCurrent = this.ticksOnCurrent;
            return ret;
         }
      }).orElseGet(this::cutIfTooLong);
   }

   private PathExecutor cutIfTooLong() {
      if (this.pathPosition > this.behavior.baritone.settings().maxPathHistoryLength.get()) {
         int cutoffAmt = this.behavior.baritone.settings().pathHistoryCutoffAmount.get();
         CutoffPath newPath = new CutoffPath(this.path, cutoffAmt, this.path.length() - 1);
         if (!newPath.getDest().equals(this.path.getDest())) {
            throw new IllegalStateException();
         } else {
            this.logDebug("Discarding earliest segment movements, length cut from " + this.path.length() + " to " + newPath.length());
            PathExecutor ret = new PathExecutor(this.behavior, newPath);
            ret.pathPosition = this.pathPosition - cutoffAmt;
            ret.currentMovementOriginalCostEstimate = this.currentMovementOriginalCostEstimate;
            if (this.costEstimateIndex != null) {
               ret.costEstimateIndex = this.costEstimateIndex - cutoffAmt;
            }

            ret.ticksOnCurrent = this.ticksOnCurrent;
            return ret;
         }
      } else {
         return this;
      }
   }

   @Override
   public IPath getPath() {
      return this.path;
   }

   public boolean failed() {
      return this.failed;
   }

   public boolean finished() {
      return this.pathPosition >= this.path.length();
   }

   public Set<BlockPos> toBreak() {
      return Collections.unmodifiableSet(this.toBreak);
   }

   public Set<BlockPos> toPlace() {
      return Collections.unmodifiableSet(this.toPlace);
   }

   public Set<BlockPos> toWalkInto() {
      return Collections.unmodifiableSet(this.toWalkInto);
   }

   public boolean isSprinting() {
      return this.sprintNextTick;
   }

   public static void writeToPacket(PathExecutor p, FriendlyByteBuf buf) {
      if (p == null) {
         buf.writeInt(-1);
      } else {
         buf.writeInt(p.pathPosition);
         writePositions(p.getPath().positions(), buf);
         writePositions(p.toBreak(), buf);
         writePositions(p.toPlace(), buf);
         writePositions(p.toWalkInto(), buf);
      }
   }

   private static void writePositions(Collection<? extends BlockPos> positions, FriendlyByteBuf buf) {
      buf.writeVarInt(positions.size());

      for (BlockPos position : positions) {
         buf.writeBlockPos(position);
      }
   }
}
