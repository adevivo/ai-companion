package baritone.pathing.movement.movements;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IEntityContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.VecUtils;
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
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class MovementPillar extends Movement {
   public MovementPillar(IBaritone baritone, BetterBlockPos start, BetterBlockPos end) {
      super(baritone, start, end, buildPositionsToBreak(baritone.getEntityContext().entity(), start), start);
   }

   public static BetterBlockPos[] buildPositionsToBreak(Entity entity, BetterBlockPos start) {
      int x = start.x;
      int y = start.y;
      int z = start.z;
      EntityDimensions dims = entity.getDimensions(Pose.STANDING);
      int requiredVerticalSpace = Mth.ceil(dims.height);
      int requiredSideSpace = CalculationContext.getRequiredSideSpace(dims);
      int sideLength = requiredSideSpace * 2 + 1;
      BetterBlockPos[] ret = new BetterBlockPos[sideLength * sideLength];
      int i = 0;

      for (int dx = -requiredSideSpace; dx <= requiredSideSpace; dx++) {
         for (int dz = -requiredSideSpace; dz <= requiredSideSpace; dz++) {
            ret[i++] = new BetterBlockPos(x + dx, y + requiredVerticalSpace, z + dz);
         }
      }

      return ret;
   }

   @Override
   public double calculateCost(CalculationContext context) {
      MutableMoveResult result = new MutableMoveResult();
      cost(context, this.src.x, this.src.y, this.src.z, result);
      return result.cost;
   }

   @Override
   protected Set<BetterBlockPos> calculateValidPositions() {
      return ImmutableSet.of(this.src, this.dest);
   }

   public static void cost(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
      BlockState fromState = context.get(x, y, z);
      boolean climbable = isClimbable(context.bsi, x, y, z);
      BlockState fromDown = context.get(x, y - 1, z);
      if (!climbable) {
         if (fromDown.is(BlockTags.CLIMBABLE)) {
            return;
         }

         if (fromDown.getBlock() instanceof SlabBlock && fromDown.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
            return;
         }
      } else if (context.width > 1) {
         return;
      }

      double totalHardness = 0.0;
      boolean swimmable = false;
      int requiredSideSpace = context.requiredSideSpace;

      for (int dx = -requiredSideSpace; dx <= requiredSideSpace; dx++) {
         for (int dz = -requiredSideSpace; dz <= requiredSideSpace; dz++) {
            int checkedX = x + dx;
            int checkedY = y + context.height;
            int checkedZ = z + dz;
            BlockState toBreak = context.get(checkedX, checkedY, checkedZ);
            BlockState underToBreak = context.get(x, checkedY - 1, z);
            Block toBreakBlock = toBreak.getBlock();
            if (toBreakBlock instanceof FenceGateBlock || !climbable && toBreakBlock instanceof ScaffoldingBlock) {
               return;
            }

            boolean water = MovementHelper.isWater(toBreak);
            if (water || MovementHelper.isWater(underToBreak)) {
               if (MovementHelper.isFlowing(checkedX, checkedY, checkedZ, toBreak, context.bsi)) {
                  return;
               }

               swimmable = true;
               if (totalHardness > 0.0) {
                  return;
               }
            }

            if (!water) {
               double hardness = MovementHelper.getMiningDurationTicks(context, checkedX, checkedY, checkedZ, toBreak, true);
               if (hardness > 0.0) {
                  if (hardness >= 1000000.0 || swimmable) {
                     return;
                  }

                  BlockState check = context.get(checkedX, checkedY + 1, checkedZ);
                  if (check.getBlock() instanceof FallingBlock
                     && (!(toBreakBlock instanceof FallingBlock) || !(underToBreak.getBlock() instanceof FallingBlock))) {
                     return;
                  }

                  totalHardness += hardness;
               }
            }
         }
      }

      if ((swimmable || !MovementHelper.isLiquid(fromState) || context.canPlaceAgainst(x, y - 1, z, fromDown))
         && (!MovementHelper.isLiquid(fromDown) || !context.assumeWalkOnWater)) {
         double placeCost = 0.0;
         if (!climbable && !swimmable) {
            placeCost = context.costOfPlacingAt(x, y, z, fromState);
            if (placeCost >= 1000000.0) {
               return;
            }

            if (fromDown.isAir()) {
               placeCost += 0.1;
            }
         }

         if (!climbable && !swimmable) {
            result.cost = JUMP_ONE_BLOCK_COST + placeCost + context.jumpPenalty + totalHardness;
            result.oxygenCost = context.oxygenCost(JUMP_ONE_BLOCK_COST + placeCost + totalHardness, Blocks.AIR.defaultBlockState());
         } else {
            result.cost = 8.51063829787234 + totalHardness * 5.0;
            result.oxygenCost = context.oxygenCost(4.25531914893617 + totalHardness * 5.0, context.get(x, y + context.height - 1, z))
               + context.oxygenCost(4.25531914893617, context.get(x, y + context.height, z));
         }
      }
   }

   private static boolean isClimbable(BlockStateInterface context, int x, int y, int z) {
      if (context.get0(x, y, z).is(BlockTags.CLIMBABLE)) {
         return true;
      } else {
         return context.get0(x, y + 1, z).is(BlockTags.CLIMBABLE) ? MovementHelper.isBlockNormalCube(context.get0(x, y - 1, z)) : false;
      }
   }

   public static BlockPos getAgainst(CalculationContext context, BetterBlockPos vine) {
      if (MovementHelper.isBlockNormalCube(context.get(vine.north()))) {
         return vine.north();
      } else if (MovementHelper.isBlockNormalCube(context.get(vine.south()))) {
         return vine.south();
      } else if (MovementHelper.isBlockNormalCube(context.get(vine.east()))) {
         return vine.east();
      } else {
         return MovementHelper.isBlockNormalCube(context.get(vine.west())) ? vine.west() : null;
      }
   }

   @Override
   public MovementState updateState(MovementState state) {
      super.updateState(state);
      if (state.getStatus() != MovementStatus.RUNNING) {
         return state;
      } else if (this.ctx.feetPos().y < this.src.y) {
         return state.setStatus(MovementStatus.UNREACHABLE);
      } else {
         BlockState fromDown = BlockStateInterface.get(this.ctx, this.src);
         if (!this.ctx.entity().isInWater() && !MovementHelper.isWater(this.ctx, this.src.up(Mth.ceil(this.ctx.entity().getBbHeight())))) {
            boolean ladder = isClimbable(((Baritone)this.baritone).bsi, this.src.x, this.src.y, this.src.z);
            Rotation rotation = RotationUtils.calcRotationFromVec3d(
               this.ctx.headPos(), VecUtils.getBlockPosCenter(this.positionToPlace), new Rotation(this.ctx.entity().getYRot(), this.ctx.entity().getXRot())
            );
            if (!ladder) {
               state.setTarget(new MovementState.MovementTarget(new Rotation(this.ctx.entity().getYRot(), rotation.getPitch()), true));
            }

            boolean blockIsThere = MovementHelper.canWalkOn(this.ctx, this.src) || ladder;
            if (ladder) {
               if (this.ctx.entity().getBbWidth() > 1.0F) {
                  this.baritone.logDirect("Large entities cannot climb ladders :/");
                  return state.setStatus(MovementStatus.UNREACHABLE);
               } else {
                  BlockPos supportingBlock = getSupportingBlock(this.baritone, this.ctx, this.src, fromDown);
                  if ((supportingBlock == null || !this.ctx.feetPos().equals(supportingBlock.above())) && !this.ctx.feetPos().equals(this.dest)) {
                     if (supportingBlock != null) {
                        MovementHelper.moveTowards(this.ctx, state, supportingBlock);
                     } else {
                        centerForAscend(this.ctx, this.dest, state, 0.27);
                     }

                     return state.setInput(Input.JUMP, true);
                  } else {
                     return state.setStatus(MovementStatus.SUCCESS);
                  }
               }
            } else if (!((Baritone)this.baritone).getInventoryBehavior().selectThrowawayForLocation(true, this.src.x, this.src.y, this.src.z)) {
               return state.setStatus(MovementStatus.UNREACHABLE);
            } else {
               state.setInput(Input.SNEAK, this.ctx.entity().getY() > this.dest.getY() || this.ctx.entity().getY() < this.src.getY() + 0.2);
               double diffX = this.ctx.entity().getX() - (this.dest.getX() + 0.5);
               double diffZ = this.ctx.entity().getZ() - (this.dest.getZ() + 0.5);
               double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
               double flatMotion = Math.sqrt(
                  this.ctx.entity().getDeltaMovement().x * this.ctx.entity().getDeltaMovement().x
                     + this.ctx.entity().getDeltaMovement().z * this.ctx.entity().getDeltaMovement().z
               );
               if (dist > 0.17) {
                  state.setInput(Input.MOVE_FORWARD, true);
                  state.setTarget(new MovementState.MovementTarget(rotation, true));
               } else if (flatMotion < 0.05) {
                  state.setInput(Input.JUMP, this.ctx.entity().getY() < this.dest.getY());
               }

               if (!blockIsThere) {
                  BlockState frState = BlockStateInterface.get(this.ctx, this.src);
                  if (!frState.isAir() && !frState.canBeReplaced()) {
                     RotationUtils.reachable(this.ctx.entity(), this.src, this.ctx.playerController().getBlockReachDistance())
                        .map(rot -> new MovementState.MovementTarget(rot, true))
                        .ifPresent(state::setTarget);
                     state.setInput(Input.JUMP, false);
                     state.setInput(Input.CLICK_LEFT, true);
                     blockIsThere = false;
                  } else if (this.ctx.entity().isShiftKeyDown()
                     && (this.ctx.isLookingAt(this.src.down()) || this.ctx.isLookingAt(this.src))
                     && this.ctx.entity().getY() > this.dest.getY() + 0.1) {
                     state.setInput(Input.CLICK_RIGHT, true);
                  }
               }

               return this.ctx.feetPos().equals(this.dest) && blockIsThere ? state.setStatus(MovementStatus.SUCCESS) : state;
            }
         } else {
            centerForAscend(this.ctx, this.dest, state, 0.2);
            state.setInput(Input.JUMP, true);
            return this.ctx.feetPos().equals(this.dest) ? state.setStatus(MovementStatus.SUCCESS) : state;
         }
      }
   }

   @Nullable
   public static BlockPos getSupportingBlock(IBaritone baritone, IEntityContext ctx, BetterBlockPos src, BlockState climbableBlock) {
      BlockPos supportingBlock;
      if (Block.isFaceFull(climbableBlock.getCollisionShape(ctx.world(), src), Direction.UP)) {
         supportingBlock = null;
      } else if (climbableBlock.getBlock() instanceof LadderBlock) {
         supportingBlock = src.offset(((Direction)climbableBlock.getValue(LadderBlock.FACING)).getOpposite());
      } else {
         supportingBlock = getAgainst(new CalculationContext(baritone), src);
      }

      return supportingBlock;
   }

   public static void centerForAscend(IEntityContext ctx, BetterBlockPos dest, MovementState state, double allowedDistance) {
      state.setTarget(
         new MovementState.MovementTarget(RotationUtils.calcRotationFromVec3d(ctx.headPos(), VecUtils.getBlockPosCenter(dest), ctx.entityRotations()), false)
      );
      Vec3 destCenter = VecUtils.getBlockPosCenter(dest);
      if (Math.abs(ctx.entity().getX() - destCenter.x) > allowedDistance || Math.abs(ctx.entity().getZ() - destCenter.z) > allowedDistance) {
         state.setInput(Input.MOVE_FORWARD, true);
      }
   }

   @Override
   protected boolean prepared(MovementState state) {
      if (this.ctx.feetPos().equals(this.src) || this.ctx.feetPos().equals(this.src.down())) {
         Block block = BlockStateInterface.getBlock(this.ctx, this.src.down());
         if (block == Blocks.LADDER || block == Blocks.VINE) {
            state.setInput(Input.SNEAK, true);
         }
      }

      return super.prepared(state);
   }
}
