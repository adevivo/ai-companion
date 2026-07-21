package baritone.pathing.movement.movements;

import baritone.api.IBaritone;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import baritone.utils.BlockStateInterface;
import baritone.utils.pathing.MutableMoveResult;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.ArrayUtils;

public class MovementAscend extends Movement {
   private int ticksWithoutPlacement = 0;

   public MovementAscend(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest) {
      super(
         baritone,
         src,
         dest,
         buildPositionsToBreak(baritone.getEntityContext().entity(), src, dest),
         buildPositionsToPlace(baritone.getEntityContext().entity(), src, dest)
      );
   }

   private static BetterBlockPos buildPositionsToPlace(LivingEntity entity, BetterBlockPos src, BetterBlockPos dest) {
      int diffX = dest.x - src.x;
      int diffZ = dest.z - src.z;

      assert Math.abs(diffX) <= 1 && Math.abs(diffZ) <= 1;

      int requiredSideSpace = CalculationContext.getRequiredSideSpace(entity.getDimensions(Pose.STANDING));
      int placeX = dest.x + diffX * requiredSideSpace;
      int placeZ = dest.z + diffZ * requiredSideSpace;
      return new BetterBlockPos(placeX, src.y, placeZ);
   }

   private static BetterBlockPos[] buildPositionsToBreak(LivingEntity entity, BetterBlockPos src, BetterBlockPos dest) {
      BetterBlockPos[] ceiling = MovementPillar.buildPositionsToBreak(entity, src);
      BetterBlockPos[] wall = MovementTraverse.buildPositionsToBreak(entity, src.up(), dest);
      return (BetterBlockPos[])ArrayUtils.addAll(ceiling, wall);
   }

   @Override
   public void reset() {
      super.reset();
      this.ticksWithoutPlacement = 0;
   }

   @Override
   public double calculateCost(CalculationContext context) {
      MutableMoveResult result = new MutableMoveResult();
      cost(context, this.src.x, this.src.y, this.src.z, this.dest.x, this.dest.z, result);
      return result.cost;
   }

   @Override
   protected Set<BetterBlockPos> calculateValidPositions() {
      BetterBlockPos prior = new BetterBlockPos(this.src.subtract(this.getDirection()).above());
      return ImmutableSet.of(this.src, this.src.up(), this.dest, prior, prior.up());
   }

   public static void cost(CalculationContext context, int x, int y, int z, int destX, int destZ, MutableMoveResult result) {
      int diffX = destX - x;
      int diffZ = destZ - z;

      assert Math.abs(diffX) <= 1 && Math.abs(diffZ) <= 1;

      int placeX = destX + diffX * context.requiredSideSpace;
      int placeZ = destZ + diffZ * context.requiredSideSpace;
      BlockState toPlace = context.get(placeX, y, placeZ);
      double additionalPlacementCost = 0.0;
      if (!MovementHelper.canWalkOn(context.bsi, placeX, y, placeZ, toPlace, context.baritone.settings())) {
         additionalPlacementCost = context.costOfPlacingAt(placeX, y, placeZ, toPlace);
         if (additionalPlacementCost >= 1000000.0) {
            return;
         }

         if (!MovementHelper.isReplaceable(placeX, y, placeZ, toPlace, context.bsi)) {
            return;
         }

         boolean foundPlaceOption = false;

         for (int i = 0; i < 5; i++) {
            int againstX = placeX + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepX();
            int againstY = y + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepY();
            int againstZ = placeZ + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepZ();
            if ((placeX - againstX != diffX || placeZ - againstZ != diffZ) && context.canPlaceAgainst(againstX, againstY, againstZ)) {
               foundPlaceOption = true;
               break;
            }
         }

         if (!foundPlaceOption) {
            return;
         }
      }

      double miningTicks = 0.0;
      BlockState srcDown = context.get(x, y - 1, z);
      if (srcDown.getBlock() != Blocks.LADDER && srcDown.getBlock() != Blocks.VINE) {
         boolean inLiquid = MovementHelper.isLiquid(srcDown);

         for (int dx = -context.requiredSideSpace; dx <= context.requiredSideSpace; dx++) {
            int dz = -context.requiredSideSpace;

            while (dz <= context.requiredSideSpace) {
               int x1 = x + dx;
               int y1 = y + context.height;
               int z1 = z + dz;
               BlockState aboveHead = context.get(x1, y1, z1);
               if (!(context.get(x1, y1 + 1, z1).getBlock() instanceof FallingBlock)
                  || !MovementHelper.canWalkThrough(context.bsi, x1, y1 - 1, z1, context.baritone.settings()) && aboveHead.getBlock() instanceof FallingBlock) {
                  miningTicks += MovementHelper.getMiningDurationTicks(context, x1, y1, z1, aboveHead, false);
                  inLiquid |= MovementHelper.isWater(aboveHead);
                  if (!(miningTicks >= 1000000.0) && (!inLiquid || !(miningTicks > 0.0))) {
                     dz++;
                     continue;
                  }

                  return;
               }

               return;
            }
         }

         boolean jumpingFromBottomSlab = !inLiquid && MovementHelper.isBottomSlab(srcDown);
         boolean jumpingToBottomSlab = !inLiquid && MovementHelper.isBottomSlab(toPlace);
         if (!jumpingFromBottomSlab || jumpingToBottomSlab) {
            double walk;
            if (jumpingToBottomSlab) {
               if (jumpingFromBottomSlab) {
                  walk = Math.max(JUMP_ONE_BLOCK_COST, 4.63284688441047);
                  walk += context.jumpPenalty;
               } else {
                  walk = 4.63284688441047;
               }
            } else if (inLiquid) {
               walk = context.waterWalkSpeed / 4.63284688441047 * Math.max(JUMP_ONE_BLOCK_COST, 4.63284688441047);
            } else {
               walk = Math.max(JUMP_ONE_BLOCK_COST, 4.63284688441047 / toPlace.getBlock().getSpeedFactor());
               walk += context.jumpPenalty;
            }

            double totalCost = walk + additionalPlacementCost;
            totalCost += miningTicks;
            if (!(totalCost >= 1000000.0)) {
               for (int dxz = -context.requiredSideSpace; dxz <= context.requiredSideSpace; dxz++) {
                  for (int dy = 0; dy < context.height; dy++) {
                     miningTicks = MovementHelper.getMiningDurationTicks(
                        context, placeX + dxz * diffZ, y + dy + 1, placeZ + dxz * diffX, dy == context.height - 1
                     );
                     totalCost += miningTicks;
                     if (totalCost >= 1000000.0 || miningTicks > 0.0 && inLiquid) {
                        return;
                     }
                  }
               }

               result.oxygenCost = context.oxygenCost(walk / 3.0, context.get(x, y + context.height - 1, z));
               result.oxygenCost = result.oxygenCost + context.oxygenCost(walk / 3.0, context.get(x, y + context.height, z));
               result.oxygenCost = result.oxygenCost + context.oxygenCost(walk / 3.0, context.get(destX, y + context.height - 1, destZ));
               result.cost = totalCost;
            }
         }
      }
   }

   @Override
   public MovementState updateState(MovementState state) {
      if (this.ctx.feetPos().y < this.src.y) {
         return state.setStatus(MovementStatus.UNREACHABLE);
      } else {
         super.updateState(state);
         if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
         } else if (!this.ctx.feetPos().equals(this.dest) && !this.ctx.feetPos().equals(this.dest.offset(this.getDirection().below()))) {
            BlockState jumpingOnto = BlockStateInterface.get(this.ctx, this.positionToPlace);
            if (!MovementHelper.canWalkOn(this.ctx, this.positionToPlace, jumpingOnto)) {
               this.ticksWithoutPlacement++;
               if (MovementHelper.attemptToPlaceABlock(state, this.baritone, this.positionToPlace, false, true) == MovementHelper.PlaceResult.READY_TO_PLACE) {
                  state.setInput(Input.SNEAK, true);
                  if (this.ctx.entity().isShiftKeyDown()) {
                     state.setInput(Input.CLICK_RIGHT, true);
                  }
               }

               if (this.ticksWithoutPlacement > 10) {
                  state.setInput(Input.MOVE_BACK, true);
               }

               return state;
            } else {
               MovementHelper.moveTowards(this.ctx, state, this.dest);
               if (MovementHelper.isBottomSlab(jumpingOnto) && !MovementHelper.isBottomSlab(BlockStateInterface.get(this.ctx, this.src.down()))) {
                  return state;
               } else if (!this.baritone.settings().assumeStep.get() && !this.canStopJumping()) {
                  int xAxis = Math.abs(this.src.getX() - this.dest.getX());
                  int zAxis = Math.abs(this.src.getZ() - this.dest.getZ());
                  double flatDistToNext = xAxis * Math.abs(this.dest.getX() + 0.5 - this.ctx.entity().getX())
                     + zAxis * Math.abs(this.dest.getZ() + 0.5 - this.ctx.entity().getZ());
                  double sideDist = zAxis * Math.abs(this.dest.getX() + 0.5 - this.ctx.entity().getX())
                     + xAxis * Math.abs(this.dest.getZ() + 0.5 - this.ctx.entity().getZ());
                  double lateralMotion = xAxis * this.ctx.entity().getDeltaMovement().z + zAxis * this.ctx.entity().getDeltaMovement().x;
                  if (Math.abs(lateralMotion) > 0.1) {
                     return state;
                  } else if (this.headBonkClear()) {
                     return state.setInput(Input.JUMP, true);
                  } else {
                     return !(flatDistToNext > 1.2) && !(sideDist > 0.2) ? state.setInput(Input.JUMP, true) : state;
                  }
               } else {
                  return state;
               }
            }
         } else {
            return state.setStatus(MovementStatus.SUCCESS);
         }
      }
   }

   private boolean canStopJumping() {
      BetterBlockPos srcUp = this.src.up();
      double entityY = this.ctx.entity().getY();
      if (entityY < srcUp.y) {
         return false;
      } else {
         return entityY <= srcUp.y + 0.1 ? !MovementHelper.isWater(this.ctx.world().getBlockState(srcUp)) : true;
      }
   }

   public boolean headBonkClear() {
      BetterBlockPos startUp = this.src.up(Mth.ceil(this.ctx.entity().getBbHeight()));

      for (int i = 0; i < 4; i++) {
         BetterBlockPos check = startUp.offset(Direction.from2DDataValue(i));
         if (!MovementHelper.canWalkThrough(this.ctx, check)) {
            return false;
         }
      }

      return true;
   }

   @Override
   public boolean safeToCancel(MovementState state) {
      return state.getStatus() != MovementStatus.RUNNING || this.ticksWithoutPlacement == 0;
   }
}
