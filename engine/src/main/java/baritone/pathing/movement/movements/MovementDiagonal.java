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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class MovementDiagonal extends Movement {
   private static final double SQRT_2 = Math.sqrt(2.0);

   public MovementDiagonal(IBaritone baritone, BetterBlockPos start, Direction dir1, Direction dir2, int dy) {
      this(baritone, start, start.offset(dir1), start.offset(dir2), dir2, dy);
   }

   private MovementDiagonal(IBaritone baritone, BetterBlockPos start, BetterBlockPos dir1, BetterBlockPos dir2, Direction drr2, int dy) {
      this(baritone, start, dir1.offset(drr2).up(dy), dir1, dir2);
   }

   private MovementDiagonal(IBaritone baritone, BetterBlockPos start, BetterBlockPos end, BetterBlockPos dir1, BetterBlockPos dir2) {
      super(baritone, start, end, computeBlocksToBreak(baritone.getEntityContext().entity(), end, dir1, dir2));
   }

   @NotNull
   private static BetterBlockPos[] computeBlocksToBreak(LivingEntity entity, BetterBlockPos end, BetterBlockPos dir1, BetterBlockPos dir2) {
      return entity.getDimensions(Pose.STANDING).height <= 1.0F
         ? new BetterBlockPos[]{dir1, dir2, end}
         : new BetterBlockPos[]{dir1, dir1.up(), dir2, dir2.up(), end, end.up()};
   }

   @Override
   protected boolean safeToCancel(MovementState state) {
      LivingEntity player = this.ctx.entity();
      double offset = 0.25;
      double x = player.getX();
      double y = player.getY() - 1.0;
      double z = player.getZ();
      if (this.ctx.feetPos().equals(this.src)) {
         return true;
      } else if (MovementHelper.canWalkOn(this.ctx, new BlockPos(this.src.x, this.src.y - 1, this.dest.z))
         && MovementHelper.canWalkOn(this.ctx, new BlockPos(this.dest.x, this.src.y - 1, this.src.z))) {
         return true;
      } else {
         return !this.ctx.feetPos().equals(new BetterBlockPos(this.src.x, this.src.y, this.dest.z))
               && !this.ctx.feetPos().equals(new BetterBlockPos(this.dest.x, this.src.y, this.src.z))
            ? true
            : MovementHelper.canWalkOn(this.ctx, new BetterBlockPos(x + offset, y, z + offset))
               || MovementHelper.canWalkOn(this.ctx, new BetterBlockPos(x + offset, y, z - offset))
               || MovementHelper.canWalkOn(this.ctx, new BetterBlockPos(x - offset, y, z + offset))
               || MovementHelper.canWalkOn(this.ctx, new BetterBlockPos(x - offset, y, z - offset));
      }
   }

   @Override
   public double calculateCost(CalculationContext context) {
      MutableMoveResult result = new MutableMoveResult();
      cost(context, this.src.x, this.src.y, this.src.z, this.dest.x, this.dest.z, result);
      return result.y != this.dest.y ? 1000000.0 : result.cost;
   }

   @Override
   protected Set<BetterBlockPos> calculateValidPositions() {
      BetterBlockPos diagA = new BetterBlockPos(this.src.x, this.src.y, this.dest.z);
      BetterBlockPos diagB = new BetterBlockPos(this.dest.x, this.src.y, this.src.z);
      if (this.dest.y < this.src.y) {
         return ImmutableSet.of(this.src, this.dest.up(), diagA, diagB, this.dest, diagA.down(), new BetterBlockPos[]{diagB.down()});
      } else {
         return this.dest.y > this.src.y
            ? ImmutableSet.of(this.src, this.src.up(), diagA, diagB, this.dest, diagA.up(), new BetterBlockPos[]{diagB.up()})
            : ImmutableSet.of(this.src, this.dest, diagA, diagB);
      }
   }

   public static void cost(CalculationContext context, int x, int y, int z, int destX, int destZ, MutableMoveResult res) {
      if (MovementHelper.canWalkThrough(context.bsi, destX, y + 1, destZ, context.baritone.settings())) {
         if (context.width <= 1 && context.height <= 2) {
            BlockState destInto = context.get(destX, y, destZ);
            boolean ascend = false;
            boolean descend = false;
            BlockState destWalkOn;
            if (!MovementHelper.canWalkThrough(context.bsi, destX, y, destZ, destInto, context.baritone.settings())) {
               ascend = true;
               if (!context.allowDiagonalAscend
                  || !MovementHelper.canWalkThrough(context.bsi, x, y + 2, z, context.baritone.settings())
                  || !MovementHelper.canWalkOn(context.bsi, destX, y, destZ, destInto, context.baritone.settings())
                  || !MovementHelper.canWalkThrough(context.bsi, destX, y + 2, destZ, context.baritone.settings())) {
                  return;
               }

               destWalkOn = destInto;
            } else {
               destWalkOn = context.get(destX, y - 1, destZ);
               if (!MovementHelper.canWalkOn(context.bsi, destX, y - 1, destZ, destWalkOn, context.baritone.settings())) {
                  descend = true;
                  if (!context.allowDiagonalDescend
                     || !MovementHelper.canWalkOn(context.bsi, destX, y - 2, destZ, context.baritone.settings())
                     || !MovementHelper.canWalkThrough(context.bsi, destX, y - 1, destZ, destWalkOn, context.baritone.settings())) {
                     return;
                  }
               }
            }

            double multiplier = 4.63284688441047 / destWalkOn.getBlock().getSpeedFactor() / 2.0;
            if (destWalkOn.is(Blocks.WATER)) {
               multiplier += context.walkOnWaterOnePenalty * SQRT_2;
            }

            Block fromDown = context.get(x, y - 1, z).getBlock();
            if (fromDown != Blocks.LADDER && fromDown != Blocks.VINE) {
               multiplier += 4.63284688441047 / fromDown.getSpeedFactor() / 2.0;
               BlockState cuttingOver1 = context.get(x, y - 1, destZ);
               if (cuttingOver1.getBlock() != Blocks.MAGMA_BLOCK && !MovementHelper.isLava(cuttingOver1)) {
                  BlockState cuttingOver2 = context.get(destX, y - 1, z);
                  if (cuttingOver2.getBlock() != Blocks.MAGMA_BLOCK && !MovementHelper.isLava(cuttingOver2)) {
                     boolean water = false;
                     BlockState startState = context.get(x, y, z);
                     Block startIn = startState.getBlock();
                     if (MovementHelper.isWater(startState) || MovementHelper.isWater(destInto)) {
                        if (ascend) {
                           return;
                        }

                        multiplier = context.waterWalkSpeed;
                        water = true;
                     }

                     boolean smol = context.height <= 1;
                     BlockState diagonalA = context.get(x, y, destZ);
                     BlockState diagonalB = context.get(destX, y, z);
                     if (ascend) {
                        boolean ATop = smol || MovementHelper.canWalkThrough(context.bsi, x, y + 2, destZ, context.baritone.settings());
                        boolean AMid = MovementHelper.canWalkThrough(context.bsi, x, y + 1, destZ, context.baritone.settings());
                        boolean ALow = MovementHelper.canWalkThrough(context.bsi, x, y, destZ, diagonalA, context.baritone.settings());
                        boolean BTop = smol || MovementHelper.canWalkThrough(context.bsi, destX, y + 2, z, context.baritone.settings());
                        boolean BMid = MovementHelper.canWalkThrough(context.bsi, destX, y + 1, z, context.baritone.settings());
                        boolean BLow = MovementHelper.canWalkThrough(context.bsi, destX, y, z, diagonalB, context.baritone.settings());
                        if ((ATop && AMid && ALow || BTop && BMid && BLow)
                           && !MovementHelper.avoidWalkingInto(diagonalA)
                           && !MovementHelper.avoidWalkingInto(diagonalB)
                           && (!ATop || !AMid || !MovementHelper.canWalkOn(context.bsi, x, y, destZ, diagonalA, context.baritone.settings()))
                           && (!BTop || !BMid || !MovementHelper.canWalkOn(context.bsi, destX, y, z, diagonalB, context.baritone.settings()))
                           && (ATop || !AMid || !ALow)
                           && (BTop || !BMid || !BLow)) {
                           res.cost = multiplier * SQRT_2 + JUMP_ONE_BLOCK_COST;
                           res.x = destX;
                           res.z = destZ;
                           res.y = y + 1;
                        }
                     } else {
                        double optionA = MovementHelper.getMiningDurationTicks(context, x, y, destZ, diagonalA, false);
                        double optionB = MovementHelper.getMiningDurationTicks(context, destX, y, z, diagonalB, false);
                        if (optionA == 0.0 || optionB == 0.0) {
                           BlockState diagonalUpA = context.get(x, y + 1, destZ);
                           if (!smol) {
                              optionA += MovementHelper.getMiningDurationTicks(context, x, y + 1, destZ, diagonalUpA, true);
                              if (optionA != 0.0 && optionB != 0.0) {
                                 return;
                              }
                           }

                           BlockState diagonalUpB = context.get(destX, y + 1, z);
                           if (optionA != 0.0
                              || (!MovementHelper.avoidWalkingInto(diagonalB) || diagonalB.getBlock() == Blocks.WATER)
                                 && (smol || !MovementHelper.avoidWalkingInto(diagonalUpB))) {
                              if (!smol) {
                                 optionB += MovementHelper.getMiningDurationTicks(context, destX, y + 1, z, diagonalUpB, true);
                                 if (optionA != 0.0 && optionB != 0.0) {
                                    return;
                                 }
                              }

                              if (optionB != 0.0
                                 || (!MovementHelper.avoidWalkingInto(diagonalA) || diagonalA.getBlock() == Blocks.WATER)
                                    && (smol || !MovementHelper.avoidWalkingInto(diagonalUpA))) {
                                 BlockState optionHeadBlock;
                                 if (optionA == 0.0 && optionB == 0.0) {
                                    if (context.canSprint && !water) {
                                       multiplier *= 0.7692444761225944;
                                    }

                                    optionHeadBlock = null;
                                 } else {
                                    multiplier *= SQRT_2 - 0.001;
                                    if (startIn == Blocks.LADDER || startIn == Blocks.VINE) {
                                       return;
                                    }

                                    optionHeadBlock = optionA != 0.0 ? diagonalUpA : diagonalUpB;
                                 }

                                 res.cost = multiplier * SQRT_2;
                                 double costPerBlock;
                                 if (optionHeadBlock == null) {
                                    costPerBlock = res.cost / 2.0;
                                 } else {
                                    costPerBlock = res.cost / 3.0;
                                    res.oxygenCost = res.oxygenCost + context.oxygenCost(costPerBlock, optionHeadBlock);
                                 }

                                 res.oxygenCost = res.oxygenCost + context.oxygenCost(costPerBlock, context.get(x, y + context.height - 1, z));
                                 if (descend) {
                                    res.cost = res.cost + Math.max(FALL_N_BLOCKS_COST[1], 0.9265693768820937);
                                    res.oxygenCost = res.oxygenCost + context.oxygenCost(costPerBlock, context.get(destX, y + context.height - 2, destZ));
                                    res.y = y - 1;
                                 } else {
                                    res.oxygenCost = res.oxygenCost + context.oxygenCost(costPerBlock, context.get(destX, y + context.height - 1, destZ));
                                    res.y = y;
                                 }

                                 res.x = destX;
                                 res.z = destZ;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public MovementState updateState(MovementState state) {
      super.updateState(state);
      if (state.getStatus() != MovementStatus.RUNNING) {
         return state;
      } else if (!this.ctx.feetPos().equals(this.dest)
         && (!MovementHelper.isWater(this.ctx, this.ctx.feetPos()) || !this.ctx.feetPos().equals(this.dest.down()))) {
         if (this.playerInValidPosition() || MovementHelper.isLiquid(this.ctx, this.src) && this.getValidPositions().contains(this.ctx.feetPos().up())) {
            if (this.dest.y > this.src.y && this.ctx.entity().getY() < this.src.y + 0.1 && this.ctx.entity().horizontalCollision) {
               state.setInput(Input.JUMP, true);
            }

            if (this.sprint()) {
               state.setInput(Input.SPRINT, true);
            }

            MovementHelper.moveTowards(this.ctx, state, this.dest);
            return state;
         } else {
            return state.setStatus(MovementStatus.UNREACHABLE);
         }
      } else {
         return state.setStatus(MovementStatus.SUCCESS);
      }
   }

   private boolean sprint() {
      if (MovementHelper.isLiquid(this.ctx, this.ctx.feetPos()) && !this.baritone.settings().sprintInWater.get()) {
         return false;
      } else {
         for (int i = 0; i < Math.min(this.positionsToBreak.length, 4); i++) {
            if (!MovementHelper.canWalkThrough(this.ctx, this.positionsToBreak[i])) {
               return false;
            }
         }

         return true;
      }
   }

   @Override
   protected boolean prepared(MovementState state) {
      return true;
   }

   @Override
   public List<BlockPos> toBreak(BlockStateInterface bsi) {
      if (this.toBreakCached != null) {
         return this.toBreakCached;
      } else {
         List<BlockPos> result = new ArrayList<>();

         for (int i = 4; i < Math.min(this.positionsToBreak.length, 6); i++) {
            if (!MovementHelper.canWalkThrough(
               bsi, this.positionsToBreak[i].x, this.positionsToBreak[i].y, this.positionsToBreak[i].z, this.ctx.baritone().settings()
            )) {
               result.add(this.positionsToBreak[i]);
            }
         }

         this.toBreakCached = result;
         return result;
      }
   }

   @Override
   public List<BlockPos> toWalkInto(BlockStateInterface bsi) {
      if (this.toWalkIntoCached == null) {
         this.toWalkIntoCached = new ArrayList<>();
      }

      List<BlockPos> result = new ArrayList<>();

      for (int i = 0; i < Math.min(this.positionsToBreak.length, 4); i++) {
         if (!MovementHelper.canWalkThrough(
            bsi, this.positionsToBreak[i].x, this.positionsToBreak[i].y, this.positionsToBreak[i].z, this.ctx.baritone().settings()
         )) {
            result.add(this.positionsToBreak[i]);
         }
      }

      this.toWalkIntoCached = result;
      return this.toWalkIntoCached;
   }
}
