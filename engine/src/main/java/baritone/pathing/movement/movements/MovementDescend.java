package baritone.pathing.movement.movements;

import baritone.api.IBaritone;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import baritone.utils.BlockStateInterface;
import baritone.utils.pathing.MutableMoveResult;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

public class MovementDescend extends Movement {
   private int numTicks = 0;

   public MovementDescend(IBaritone baritone, BetterBlockPos start, BetterBlockPos end) {
      super(baritone, start, end, buildPositionsToBreak(baritone.getEntityContext().entity(), start, end), end.down());
   }

   @NotNull
   private static BetterBlockPos[] buildPositionsToBreak(LivingEntity entity, BetterBlockPos start, BetterBlockPos end) {
      BetterBlockPos[] wall = MovementTraverse.buildPositionsToBreak(entity, start, end.up());
      BetterBlockPos[] floor = MovementDownward.buildPositionsToBreak(entity, end);
      return (BetterBlockPos[])ArrayUtils.addAll(wall, floor);
   }

   @Override
   public void reset() {
      super.reset();
      this.numTicks = 0;
   }

   @Override
   public double calculateCost(CalculationContext context) {
      MutableMoveResult result = new MutableMoveResult();
      cost(context, this.src.x, this.src.y, this.src.z, this.dest.x, this.dest.z, result);
      return result.y != this.dest.y ? 1000000.0 : result.cost;
   }

   @Override
   protected Set<BetterBlockPos> calculateValidPositions() {
      return ImmutableSet.of(this.src, this.dest.up(), this.dest);
   }

   public static void cost(CalculationContext context, int x, int y, int z, int destX, int destZ, MutableMoveResult res) {
      double frontBreak = 0.0;
      BlockState destDown = context.get(destX, y - 1, destZ);
      res.x = destX;
      res.y = y - 1;
      res.z = destZ;
      if (!destDown.is(Blocks.SCAFFOLDING) || !(Boolean)destDown.getValue(ScaffoldingBlock.BOTTOM)) {
         frontBreak += MovementHelper.getMiningDurationTicks(context, destX, y - 1, destZ, destDown, false);
         if (!(frontBreak >= 1000000.0)) {
            BlockState destUp = context.get(destX, y, destZ);
            if (!destUp.is(Blocks.SCAFFOLDING) || !(Boolean)destUp.getValue(ScaffoldingBlock.BOTTOM)) {
               frontBreak += MovementHelper.getMiningDurationTicks(context, destX, y, destZ, destUp, false);
               if (!(frontBreak >= 1000000.0)) {
                  frontBreak += MovementHelper.getMiningDurationTicks(context, destX, y + 1, destZ, true);
                  if (!(frontBreak >= 1000000.0)) {
                     BlockState fromDown = context.get(x, y - 1, z);
                     if (!fromDown.is(BlockTags.CLIMBABLE)) {
                        BlockState below = context.get(destX, y - 2, destZ);
                        if (!MovementHelper.canWalkOn(context.bsi, destX, y - 2, destZ, below, context.baritone.settings())) {
                           dynamicFallCost(context, x, y, z, destX, destZ, frontBreak, below, res);
                           res.oxygenCost = res.oxygenCost + context.oxygenCost(3.7062775075283763 + frontBreak, context.get(x, y + context.height - 1, z));
                        } else if (destDown.getBlock() != Blocks.LADDER && destDown.getBlock() != Blocks.VINE) {
                           boolean water = MovementHelper.isWater(destUp);
                           double waterModifier = water ? context.waterWalkSpeed / 4.63284688441047 : 1.0;
                           double walk = waterModifier * (3.7062775075283763 / fromDown.getBlock().getSpeedFactor());
                           double fall = waterModifier * Math.max(FALL_N_BLOCKS_COST[1], 0.9265693768820937);
                           double totalCost = frontBreak + (walk + fall);
                           res.cost = totalCost;
                           res.oxygenCost = context.oxygenCost(walk / 2.0 + frontBreak, context.get(x, y + context.height - 1, z));
                           res.oxygenCost = res.oxygenCost + context.oxygenCost(fall / 2.0, context.get(destX, y + context.height - 2, destZ));
                           res.oxygenCost = res.oxygenCost + context.oxygenCost(walk / 2.0 + fall / 2.0, context.get(destX, y + context.height - 1, destZ));
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static boolean dynamicFallCost(
      CalculationContext context, int x, int y, int z, int destX, int destZ, double frontBreak, BlockState below, MutableMoveResult res
   ) {
      if (frontBreak != 0.0 && context.get(destX, y + 2, destZ).getBlock() instanceof FallingBlock) {
         return false;
      } else if (!MovementHelper.canWalkThrough(context.bsi, destX, y - 2, destZ, below, context.baritone.settings())) {
         return false;
      } else {
         double costSoFar = 0.0;
         int effectiveStartHeight = y;
         int fallHeight = 3;

         while (true) {
            int newY = y - fallHeight;
            if (newY < context.worldBottom) {
               return false;
            }

            BlockState ontoBlock = context.get(destX, newY, destZ);
            int unprotectedFallHeight = fallHeight - (y - effectiveStartHeight);
            double fallCost = FALL_N_BLOCKS_COST[unprotectedFallHeight] + costSoFar;
            double tentativeCost = 3.7062775075283763 + fallCost + frontBreak;
            if (MovementHelper.isWater(ontoBlock)) {
               if (!MovementHelper.canWalkThrough(context.bsi, destX, newY, destZ, ontoBlock, context.baritone.settings())) {
                  return false;
               }

               if (context.assumeWalkOnWater) {
                  return false;
               }

               if (MovementHelper.isFlowing(destX, newY, destZ, ontoBlock, context.bsi)) {
                  return false;
               }

               if (!MovementHelper.canWalkOn(context.bsi, destX, newY - 1, destZ, context.baritone.settings())) {
                  return false;
               }

               res.x = destX;
               res.y = newY;
               res.z = destZ;
               res.cost = tentativeCost;
               res.oxygenCost = context.oxygenCost(fallCost, Blocks.AIR.defaultBlockState());
               return false;
            }

            if (unprotectedFallHeight > 11 || ontoBlock.getBlock() != Blocks.VINE && ontoBlock.getBlock() != Blocks.LADDER) {
               if (!MovementHelper.canWalkThrough(context.bsi, destX, newY, destZ, ontoBlock, context.baritone.settings())) {
                  if (!MovementHelper.canWalkOn(context.bsi, destX, newY, destZ, ontoBlock, context.baritone.settings())) {
                     return false;
                  }

                  if (MovementHelper.isBottomSlab(ontoBlock)) {
                     return false;
                  }

                  if (unprotectedFallHeight <= context.maxFallHeightNoWater + 1) {
                     res.x = destX;
                     res.y = newY + 1;
                     res.z = destZ;
                     res.cost = tentativeCost;
                     res.oxygenCost = context.oxygenCost(fallCost, Blocks.AIR.defaultBlockState());
                     return false;
                  }

                  if (context.hasWaterBucket && unprotectedFallHeight <= context.maxFallHeightBucket + 1) {
                     res.x = destX;
                     res.y = newY + 1;
                     res.z = destZ;
                     res.cost = tentativeCost + context.placeBucketCost();
                     res.oxygenCost = context.oxygenCost(fallCost, Blocks.AIR.defaultBlockState());
                     return true;
                  }

                  res.x = destX;
                  res.y = newY + 1;
                  res.z = destZ;
                  return false;
               }
            } else {
               costSoFar += FALL_N_BLOCKS_COST[unprotectedFallHeight - 1];
               costSoFar += 6.666666666666667;
               effectiveStartHeight = newY;
            }

            fallHeight++;
         }
      }
   }

   @Override
   public MovementState updateState(MovementState state) {
      super.updateState(state);
      if (state.getStatus() != MovementStatus.RUNNING) {
         return state;
      } else {
         BlockPos playerFeet = this.ctx.feetPos();
         BlockPos fakeDest = new BlockPos(this.dest.getX() * 2 - this.src.getX(), this.dest.getY(), this.dest.getZ() * 2 - this.src.getZ());
         if (!playerFeet.equals(this.dest) && !playerFeet.equals(fakeDest)
            || !MovementHelper.isLiquid(this.ctx, this.dest) && !(this.ctx.entity().getY() - this.dest.getY() < 0.5)) {
            double diffX = this.ctx.entity().getX() - (this.dest.getX() + 0.5);
            double diffZ = this.ctx.entity().getZ() - (this.dest.getZ() + 0.5);
            double ab = Math.sqrt(diffX * diffX + diffZ * diffZ);
            if (ab < 0.2
               && (
                  this.ctx.world().getBlockState(this.dest).is(Blocks.SCAFFOLDING) || this.ctx.entity().isUnderWater() && this.ctx.entity().getY() > this.src.y
               )) {
               state.setInput(Input.SNEAK, true);
            }

            if (this.safeMode()) {
               double destX = (this.src.getX() + 0.5) * 0.17 + (this.dest.getX() + 0.5) * 0.83;
               double destZ = (this.src.getZ() + 0.5) * 0.17 + (this.dest.getZ() + 0.5) * 0.83;
               LivingEntity player = this.ctx.entity();
               state.setTarget(
                     new MovementState.MovementTarget(
                        new Rotation(
                           RotationUtils.calcRotationFromVec3d(
                                 this.ctx.headPos(), new Vec3(destX, this.dest.getY(), destZ), new Rotation(player.getYRot(), player.getXRot())
                              )
                              .getYaw(),
                           player.getXRot()
                        ),
                        false
                     )
                  )
                  .setInput(Input.MOVE_FORWARD, true);
               return state;
            } else {
               double x = this.ctx.entity().getX() - (this.src.getX() + 0.5);
               double z = this.ctx.entity().getZ() - (this.src.getZ() + 0.5);
               double fromStart = Math.sqrt(x * x + z * z);
               if (!playerFeet.equals(this.dest) || ab > 0.25) {
                  if (this.numTicks++ < 20 && fromStart < 1.25) {
                     MovementHelper.moveTowards(this.ctx, state, fakeDest);
                  } else {
                     MovementHelper.moveTowards(this.ctx, state, this.dest);
                  }
               }

               return state;
            }
         } else {
            return state.setStatus(MovementStatus.SUCCESS);
         }
      }
   }

   public boolean safeMode() {
      BlockPos into = this.dest.subtract(this.src.down()).offset(this.dest);
      if (this.skipToAscend()) {
         return true;
      } else {
         for (int y = 0; y <= 2; y++) {
            BlockState state = BlockStateInterface.get(this.ctx, into.above(y));
            if (MovementHelper.avoidWalkingInto(state) && (!MovementHelper.isWater(state) || !this.baritone.settings().allowSwimming.get())) {
               return true;
            }
         }

         return false;
      }
   }

   public boolean skipToAscend() {
      BlockPos into = this.dest.subtract(this.src.down()).offset(this.dest);
      return !MovementHelper.canWalkThrough(this.ctx, new BetterBlockPos(into))
         && MovementHelper.canWalkThrough(this.ctx, new BetterBlockPos(into).up())
         && MovementHelper.canWalkThrough(this.ctx, new BetterBlockPos(into).up(2));
   }
}
