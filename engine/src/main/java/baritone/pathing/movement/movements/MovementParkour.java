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
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.WaterFluid;

public class MovementParkour extends Movement {
   private static final BetterBlockPos[] EMPTY = new BetterBlockPos[0];
   private final Direction direction;
   private final int dist;
   private final boolean ascend;

   private MovementParkour(IBaritone baritone, BetterBlockPos src, int dist, Direction dir, boolean ascend) {
      super(baritone, src, src.offset(dir, dist).up(ascend ? 1 : 0), EMPTY, src.offset(dir, dist).down(ascend ? 0 : 1));
      this.direction = dir;
      this.dist = dist;
      this.ascend = ascend;
   }

   public static MovementParkour cost(CalculationContext context, BetterBlockPos src, Direction direction) {
      MutableMoveResult res = new MutableMoveResult();
      cost(context, src.x, src.y, src.z, direction, res);
      int dist = Math.abs(res.x - src.x) + Math.abs(res.z - src.z);
      return new MovementParkour(context.getBaritone(), src, dist, direction, res.y > src.y);
   }

   public static void cost(CalculationContext context, int x, int y, int z, Direction dir, MutableMoveResult res) {
      if (context.allowParkour) {
         if (context.height <= 2 && context.width <= 1) {
            if (y != context.worldTop || context.allowJumpAt256) {
               int xDiff = dir.getStepX();
               int zDiff = dir.getStepZ();
               if (MovementHelper.fullyPassable(context, x + xDiff, y, z + zDiff)) {
                  BlockState adj = context.get(x + xDiff, y - 1, z + zDiff);
                  if (!MovementHelper.canWalkOn(context.bsi, x + xDiff, y - 1, z + zDiff, adj, context.baritone.settings())) {
                     if (!MovementHelper.avoidWalkingInto(adj) || adj.getFluidState().getType() instanceof WaterFluid) {
                        if (MovementHelper.fullyPassable(context, x + xDiff, y + 1, z + zDiff)) {
                           if (MovementHelper.fullyPassable(context, x + xDiff, y + 2, z + zDiff)) {
                              if (MovementHelper.fullyPassable(context, x, y + 2, z)) {
                                 BlockState standingOn = context.get(x, y - 1, z);
                                 if (standingOn.getBlock() != Blocks.VINE
                                    && standingOn.getBlock() != Blocks.LADDER
                                    && !(standingOn.getBlock() instanceof StairBlock)
                                    && !MovementHelper.isBottomSlab(standingOn)
                                    && standingOn.getFluidState().getType() == Fluids.EMPTY) {
                                    int maxJump;
                                    if (standingOn.getBlock().getSpeedFactor() < 0.8) {
                                       maxJump = 2;
                                    } else if (context.canSprint) {
                                       maxJump = 4;
                                    } else {
                                       maxJump = 3;
                                    }

                                    boolean smol = context.height <= 1;

                                    for (int i = 2; i <= maxJump; i++) {
                                       int destX = x + xDiff * i;
                                       int destZ = z + zDiff * i;
                                       if (!MovementHelper.fullyPassable(context, destX, y + 1, destZ)) {
                                          return;
                                       }

                                       if (!smol && !MovementHelper.fullyPassable(context, destX, y + 2, destZ)) {
                                          return;
                                       }

                                       BlockState destInto = context.bsi.get0(destX, y, destZ);
                                       if (!MovementHelper.fullyPassable(context.bsi.access, context.bsi.isPassableBlockPos.set(destX, y, destZ), destInto)) {
                                          if (i <= 3
                                             && context.allowParkourAscend
                                             && context.canSprint
                                             && MovementHelper.canWalkOn(context.bsi, destX, y, destZ, destInto, context.baritone.settings())
                                             && checkOvershootSafety(context.bsi, destX + xDiff, y + 1, destZ + zDiff)) {
                                             res.x = destX;
                                             res.y = y + 1;
                                             res.z = destZ;
                                             res.cost = i * 3.563791874554526 + context.jumpPenalty;
                                             res.oxygenCost = context.oxygenCost(res.cost, Blocks.AIR.defaultBlockState());
                                          }

                                          return;
                                       }

                                       BlockState landingOn = context.bsi.get0(destX, y - 1, destZ);
                                       if (!(landingOn.getBlock() instanceof FarmBlock)
                                          && MovementHelper.canWalkOn(context.bsi, destX, y - 1, destZ, landingOn, context.baritone.settings())) {
                                          if (checkOvershootSafety(context.bsi, destX + xDiff, y, destZ + zDiff)) {
                                             res.x = destX;
                                             res.y = y;
                                             res.z = destZ;
                                             res.cost = costFromJumpDistance(i) + context.jumpPenalty;
                                             res.oxygenCost = context.oxygenCost(res.cost, Blocks.AIR.defaultBlockState());
                                          }

                                          return;
                                       }

                                       if (!MovementHelper.fullyPassable(context, destX, y + context.height + 1, destZ)) {
                                          return;
                                       }
                                    }

                                    if (maxJump == 4) {
                                       if (context.allowParkourPlace) {
                                          int destXx = x + 4 * xDiff;
                                          int destZx = z + 4 * zDiff;
                                          BlockState toReplace = context.get(destXx, y - 1, destZx);
                                          double placeCost = context.costOfPlacingAt(destXx, y - 1, destZx, toReplace);
                                          if (!(placeCost >= 1000000.0)) {
                                             if (MovementHelper.isReplaceable(destXx, y - 1, destZx, toReplace, context.bsi)) {
                                                if (checkOvershootSafety(context.bsi, destXx + xDiff, y, destZx + zDiff)) {
                                                   for (int i = 0; i < 5; i++) {
                                                      int againstX = destXx + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepX();
                                                      int againstY = y - 1 + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepY();
                                                      int againstZ = destZx + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepZ();
                                                      if ((againstX != x + xDiff * 3 || againstZ != z + zDiff * 3)
                                                         && MovementHelper.canPlaceAgainst(context.bsi, againstX, againstY, againstZ)) {
                                                         res.x = destXx;
                                                         res.y = y;
                                                         res.z = destZx;
                                                         res.cost = costFromJumpDistance(4) + placeCost + context.jumpPenalty;
                                                         res.oxygenCost = context.oxygenCost(res.cost, Blocks.AIR.defaultBlockState());
                                                         return;
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
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean checkOvershootSafety(BlockStateInterface bsi, int x, int y, int z) {
      return !MovementHelper.avoidWalkingInto(bsi.get0(x, y, z)) && !MovementHelper.avoidWalkingInto(bsi.get0(x, y + 1, z));
   }

   private static double costFromJumpDistance(int dist) {
      switch (dist) {
         case 2:
            return 9.26569376882094;
         case 3:
            return 13.89854065323141;
         case 4:
            return 14.255167498218103;
         default:
            throw new IllegalStateException("LOL " + dist);
      }
   }

   @Override
   public double calculateCost(CalculationContext context) {
      MutableMoveResult res = new MutableMoveResult();
      cost(context, this.src.x, this.src.y, this.src.z, this.direction, res);
      return res.x == this.dest.x && res.y == this.dest.y && res.z == this.dest.z ? res.cost : 1000000.0;
   }

   @Override
   protected Set<BetterBlockPos> calculateValidPositions() {
      Set<BetterBlockPos> set = new HashSet<>();

      for (int i = 0; i <= this.dist; i++) {
         for (int y = 0; y < 2; y++) {
            set.add(this.src.offset(this.direction, i).up(y));
         }
      }

      return set;
   }

   @Override
   public boolean safeToCancel(MovementState state) {
      return state.getStatus() != MovementStatus.RUNNING;
   }

   @Override
   public MovementState updateState(MovementState state) {
      super.updateState(state);
      if (state.getStatus() != MovementStatus.RUNNING) {
         return state;
      } else if (this.ctx.feetPos().y < this.src.y) {
         this.baritone.logDebug("sorry");
         return state.setStatus(MovementStatus.UNREACHABLE);
      } else {
         if (this.ctx.feetPos().equals(this.dest)) {
            Block d = BlockStateInterface.getBlock(this.ctx, this.dest);
            if (d == Blocks.VINE || d == Blocks.LADDER) {
               return state.setStatus(MovementStatus.SUCCESS);
            }

            if (this.ctx.entity().getDeltaMovement().normalize().dot(this.ctx.entity().getLookAngle()) > 0.5) {
               state.setInput(Input.MOVE_BACK, true);
            }

            if (this.ctx.entity().getY() - this.ctx.feetPos().getY() < 0.094) {
               state.setStatus(MovementStatus.SUCCESS);
            }
         } else {
            MovementHelper.moveTowards(this.ctx, state, this.dest);
            if (this.dist >= 4 || this.ascend) {
               state.setInput(Input.SPRINT, true);
            }

            if (!this.ctx.feetPos().equals(this.src)) {
               if (!this.ctx.feetPos().equals(this.src.offset(this.direction)) && !(this.ctx.entity().getY() - this.src.y > 1.0E-4)) {
                  if (!this.ctx.feetPos().equals(this.dest.offset(this.direction, -1))) {
                     state.setInput(Input.SPRINT, false);
                     if (this.ctx.feetPos().equals(this.src.offset(this.direction, -1))) {
                        MovementHelper.moveTowards(this.ctx, state, this.src);
                     } else {
                        MovementHelper.moveTowards(this.ctx, state, this.src.offset(this.direction, -1));
                     }
                  }
               } else {
                  if (!MovementHelper.canWalkOn(this.ctx, this.dest.down())
                     && !this.ctx.entity().onGround()
                     && MovementHelper.attemptToPlaceABlock(state, this.baritone, this.dest.down(), true, false) == MovementHelper.PlaceResult.READY_TO_PLACE) {
                     state.setInput(Input.CLICK_RIGHT, true);
                  }

                  if (this.dist == 3 && !this.ascend) {
                     double xDiff = this.src.x + 0.5 - this.ctx.entity().getX();
                     double zDiff = this.src.z + 0.5 - this.ctx.entity().getZ();
                     double distFromStart = Math.max(Math.abs(xDiff), Math.abs(zDiff));
                     if (distFromStart < 0.7) {
                        return state;
                     }
                  }

                  state.setInput(Input.JUMP, true);
               }
            }
         }

         return state;
      }
   }
}
