package baritone.pathing.movement.movements;

import baritone.api.IBaritone;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
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
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MovementTraverse extends Movement {
   private boolean wasTheBridgeBlockAlwaysThere = true;

   public MovementTraverse(IBaritone baritone, BetterBlockPos from, BetterBlockPos to) {
      super(baritone, from, to, buildPositionsToBreak(baritone.getEntityContext().entity(), from, to), to.down());
   }

   @Override
   public void reset() {
      super.reset();
      this.wasTheBridgeBlockAlwaysThere = true;
   }

   @Override
   public double calculateCost(CalculationContext context) {
      MutableMoveResult result = new MutableMoveResult();
      cost(context, this.src.x, this.src.y, this.src.z, this.dest.x, this.dest.z, result);
      return result.cost;
   }

   @Override
   protected Set<BetterBlockPos> calculateValidPositions() {
      return ImmutableSet.of(this.src, this.dest);
   }

   public static BetterBlockPos[] buildPositionsToBreak(Entity e, BetterBlockPos from, BetterBlockPos to) {
      int x = from.x;
      int y = from.y;
      int z = from.z;
      int destX = to.x;
      int destZ = to.z;
      int diffX = destX - x;
      int diffZ = destZ - z;
      EntityDimensions dimensions = e.getDimensions(Pose.STANDING);
      int requiredSideSpace = CalculationContext.getRequiredSideSpace(dimensions);
      int checkedXShift = diffX * requiredSideSpace;
      int checkedZShift = diffZ * requiredSideSpace;
      int checkedX = destX + checkedXShift;
      int checkedZ = destZ + checkedZShift;
      int height = Mth.ceil(dimensions.height);
      int requiredForwardSpace = requiredSideSpace == 0 ? 1 : 2;
      int volume = requiredForwardSpace * (requiredSideSpace * 2 + 1) * height;
      int i = 0;
      BetterBlockPos[] ret = new BetterBlockPos[volume];

      for (int df = 0; df < requiredForwardSpace; df++) {
         for (int ds = -requiredSideSpace; ds <= requiredSideSpace; ds++) {
            for (int dy = 0; dy < height; dy++) {
               ret[i++] = new BetterBlockPos(checkedX + diffZ * ds - diffX * df, y + dy, checkedZ + diffX * ds - diffZ * df);
            }
         }
      }

      return ret;
   }

   public static void cost(CalculationContext context, int x, int y, int z, int destX, int destZ, MutableMoveResult result) {
      BlockState destOn = context.get(destX, y - 1, destZ);
      BlockState srcOn = context.get(x, y - 1, z);
      Block srcOnBlock = srcOn.getBlock();
      int movX = destX - x;
      int movZ = destZ - z;
      int checkedXShift = movX * context.requiredSideSpace;
      int checkedZShift = movZ * context.requiredSideSpace;
      int checkedX = destX + checkedXShift;
      int checkedZ = destZ + checkedZShift;
      if (MovementHelper.canWalkOn(context.bsi, destX, y - 1, destZ, destOn, context.baritone.settings())) {
         double WC = 0.0;
         boolean water = false;
         BlockState destHeadState = context.get(destX, y + context.height - 1, destZ);
         if (MovementHelper.isWater(destHeadState)) {
            WC = context.waterWalkSpeed;
            water = true;
         } else {
            for (int dy = 0; dy < context.height - 1; dy++) {
               if (MovementHelper.isWater(context.get(destX, y + dy, destZ))) {
                  WC = context.waterWalkSpeed;
                  water = true;
                  break;
               }
            }
         }

         if (!water) {
            if (destOn.getBlock() == Blocks.WATER) {
               WC = context.walkOnWaterOnePenalty;
            } else {
               WC = 4.63284688441047 / destOn.getBlock().getSpeedFactor() / 2.0;
            }

            WC += 4.63284688441047 / srcOnBlock.getSpeedFactor() / 2.0;
         }

         double hardness = 0.0;
         BlockState srcHeadState = context.get(x, y + context.height - 1, z);
         int hardnessModifier = !MovementHelper.isWater(srcHeadState) && srcOnBlock != Blocks.LADDER && srcOnBlock != Blocks.VINE ? 1 : 5;

         for (int dxz = -context.requiredSideSpace; dxz <= context.requiredSideSpace; dxz++) {
            for (int dyx = 0; dyx < context.height; dyx++) {
               hardness += MovementHelper.getMiningDurationTicks(context, checkedX + dxz * movZ, y + dyx, checkedZ + dxz * movX, dyx == context.height - 1)
                  * hardnessModifier;
               if (hardness >= 1000000.0) {
                  return;
               }
            }
         }

         if (hardness == 0.0 && !water && context.canSprint) {
            WC *= 0.7692444761225944;
         }

         result.cost = WC + hardness;
         result.oxygenCost = context.oxygenCost(WC / 2.0 + hardness, srcHeadState) + context.oxygenCost(WC / 2.0, destHeadState);
      } else {
         if (srcOnBlock == Blocks.LADDER || srcOnBlock == Blocks.VINE) {
            return;
         }

         if (MovementHelper.isReplaceable(destX, y - 1, destZ, destOn, context.bsi)) {
            boolean throughWater = false;

            for (int dyxx = 0; dyxx < context.height; dyxx++) {
               if (MovementHelper.isWater(context.get(destX, y + dyxx, destZ))) {
                  throughWater = true;
                  if (MovementHelper.isWater(destOn)) {
                     return;
                  }
                  break;
               }
            }

            double placeCost = context.costOfPlacingAt(destX, y - 1, destZ, destOn);
            if (placeCost >= 1000000.0) {
               return;
            }

            double hardness = 0.0;

            for (int dxz = -context.requiredSideSpace; dxz <= context.requiredSideSpace; dxz++) {
               for (int dyxxx = 0; dyxxx < context.height; dyxxx++) {
                  hardness += MovementHelper.getMiningDurationTicks(
                     context, checkedX + dxz * movZ, y + dyxxx, checkedZ + dxz * movX, dyxxx == context.height - 1
                  );
                  if (hardness >= 1000000.0) {
                     return;
                  }
               }
            }

            double WCx = throughWater ? context.waterWalkSpeed : 4.63284688441047;

            for (int i = 0; i < 5; i++) {
               int againstX = destX + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepX();
               int againstY = y - 1 + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepY();
               int againstZ = destZ + HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i].getStepZ();
               if ((againstX != x || againstZ != z) && MovementHelper.canPlaceAgainst(context.bsi, againstX, againstY, againstZ)) {
                  result.cost = WCx + placeCost + hardness;
                  return;
               }
            }

            if (!srcOn.canBeReplaced() && !srcOn.isCollisionShapeFullBlock(context.world, BlockPos.ZERO)) {
               return;
            }

            if (srcOn.getFluidState().getType() instanceof WaterFluid) {
               return;
            }

            WCx *= 3.3207692307692307;
            result.cost = WCx + placeCost + hardness;
            result.oxygenCost = context.oxygenCost(result.cost, Blocks.AIR.defaultBlockState());
         }
      }
   }

   @Override
   public MovementState updateState(MovementState state) {
      super.updateState(state);
      BlockState[] bss = new BlockState[this.positionsToBreak.length];

      for (int i = 0; i < this.positionsToBreak.length; i++) {
         bss[i] = BlockStateInterface.get(this.ctx, this.positionsToBreak[i]);
      }

      if (state.getStatus() != MovementStatus.RUNNING) {
         if (!this.baritone.settings().walkWhileBreaking.get()) {
            return state;
         } else if (state.getStatus() != MovementStatus.PREPPING) {
            return state;
         } else {
            for (BlockState pb : bss) {
               if (MovementHelper.avoidWalkingInto(pb)) {
                  return state;
               }
            }

            double dist = Math.max(Math.abs(this.ctx.entity().getX() - (this.dest.getX() + 0.5)), Math.abs(this.ctx.entity().getZ() - (this.dest.getZ() + 0.5)));
            if (dist < 0.83) {
               return state;
            } else if (state.getTarget().getRotation().isEmpty()) {
               return state;
            } else {
               EntityDimensions dims = this.ctx.entity().getDimensions(this.ctx.entity().getPose());
               if (!(dims.width > 1.0F) && !(dims.height < 1.0F) && !(dims.height > 2.0F)) {
                  float yawToDest = RotationUtils.calcRotationFromVec3d(
                        this.ctx.headPos(), VecUtils.calculateBlockCenter(this.ctx.world(), this.dest), this.ctx.entityRotations()
                     )
                     .getYaw();
                  float pitchToBreak = state.getTarget().getRotation().get().getPitch();
                  if (MovementHelper.isBlockNormalCube(bss[0])
                     || bss[0].getBlock() instanceof AirBlock && (MovementHelper.isBlockNormalCube(bss[1]) || bss[1].getBlock() instanceof AirBlock)) {
                     pitchToBreak = 26.0F;
                  }

                  return state.setTarget(new MovementState.MovementTarget(new Rotation(yawToDest, pitchToBreak), true))
                     .setInput(Input.MOVE_FORWARD, true)
                     .setInput(Input.SPRINT, true);
               } else {
                  return state;
               }
            }
         }
      } else {
         state.setInput(Input.SNEAK, false);
         BlockState fd = BlockStateInterface.get(this.ctx, this.src.down());
         boolean ladder = fd.is(BlockTags.CLIMBABLE);

         for (BlockState bs : bss) {
            if (this.tryOpenDoors(state, bs, this.dest, this.src)) {
               return state;
            }
         }

         if (this.tryOpenDoors(state, BlockStateInterface.get(this.ctx, this.src), this.src, this.dest)) {
            return state;
         } else {
            boolean isTheBridgeBlockThere = MovementHelper.canWalkOn(this.ctx, this.positionToPlace) || ladder;
            BlockPos feet = this.ctx.feetPos();
            BlockPos standingOnPos = feet.below();
            BlockState standingOn = BlockStateInterface.get(this.ctx, standingOnPos);
            if (MovementHelper.isWater(standingOn) && this.ctx.entity().getY() < this.src.getY() + Math.random() * 0.2) {
               state.setInput(Input.JUMP, true);
            } else if (feet.getY() != this.dest.getY() && !ladder) {
               this.baritone.logDebug("Wrong Y coordinate");
               if (feet.getY() < this.dest.getY()) {
                  MovementHelper.moveTowards(this.ctx, state, this.dest);
                  return state.setInput(Input.MOVE_FORWARD, false).setInput(Input.JUMP, true);
               }

               return state;
            }

            if (isTheBridgeBlockThere) {
               if (feet.equals(this.dest)) {
                  return state.setStatus(MovementStatus.SUCCESS);
               }

               if (this.baritone.settings().overshootTraverse.get()
                  && (feet.equals(this.dest.offset(this.getDirection())) || feet.equals(this.dest.offset(this.getDirection()).offset(this.getDirection())))) {
                  return state.setStatus(MovementStatus.SUCCESS);
               }

               BlockState lowBs = BlockStateInterface.get(this.ctx, this.src);
               Block low = lowBs.getBlock();
               Block high = BlockStateInterface.get(this.ctx, this.src.up()).getBlock();
               if (this.ctx.entity().getY() > this.src.y + 0.1
                  && !this.ctx.entity().onGround()
                  && (low == Blocks.VINE || low == Blocks.LADDER || high == Blocks.VINE || high == Blocks.LADDER)
                  && !MovementHelper.isLiquid(lowBs)) {
                  return state;
               }

               BlockPos into = this.dest.subtract(this.src).offset(this.dest);
               BlockState intoBelow = BlockStateInterface.get(this.ctx, into);
               BlockState intoAbove = BlockStateInterface.get(this.ctx, into.above());
               if (this.wasTheBridgeBlockAlwaysThere
                  && (!MovementHelper.isLiquid(this.ctx, feet) || this.baritone.settings().sprintInWater.get())
                  && (!MovementHelper.avoidWalkingInto(intoBelow) || MovementHelper.isWater(intoBelow))
                  && !MovementHelper.avoidWalkingInto(intoAbove)) {
                  state.setInput(Input.SPRINT, true);
               }

               BlockState destDown = BlockStateInterface.get(this.ctx, this.dest.down());
               if (feet.getY() != this.dest.getY() && ladder && destDown.is(BlockTags.CLIMBABLE)) {
                  BlockPos against = MovementPillar.getSupportingBlock(this.baritone, this.ctx, this.src, destDown);
                  if (against != null) {
                     MovementHelper.moveTowards(this.ctx, state, against);
                  } else {
                     MovementPillar.centerForAscend(this.ctx, this.dest, state, 0.25);
                  }

                  state.setInput(Input.JUMP, true);
               } else {
                  MovementHelper.moveTowards(this.ctx, state, this.dest.up());
               }
            } else {
               this.wasTheBridgeBlockAlwaysThere = false;
               VoxelShape collisionShape = standingOn.getCollisionShape(this.ctx.world(), standingOnPos);
               if (!collisionShape.isEmpty() && collisionShape.bounds().maxY < 1.0) {
                  double dist = Math.max(
                     Math.abs(this.dest.getX() + 0.5 - this.ctx.entity().getX()), Math.abs(this.dest.getZ() + 0.5 - this.ctx.entity().getZ())
                  );
                  if (dist < 0.85) {
                     MovementHelper.moveTowards(this.ctx, state, this.dest);
                     return state.setInput(Input.MOVE_FORWARD, false).setInput(Input.MOVE_BACK, true);
                  }
               }

               double dist1 = Math.max(
                  Math.abs(this.ctx.entity().getX() - (this.dest.getX() + 0.5)), Math.abs(this.ctx.entity().getZ() - (this.dest.getZ() + 0.5))
               );
               MovementHelper.PlaceResult p = MovementHelper.attemptToPlaceABlock(state, this.baritone, this.dest.down(), false, true);
               if ((p == MovementHelper.PlaceResult.READY_TO_PLACE || dist1 < 0.6) && !this.baritone.settings().assumeSafeWalk.get()) {
                  state.setInput(Input.SNEAK, true);
               }

               switch (p) {
                  case READY_TO_PLACE:
                     if (this.ctx.entity().isShiftKeyDown() || this.baritone.settings().assumeSafeWalk.get()) {
                        state.setInput(Input.CLICK_RIGHT, true);
                     }

                     return state;
                  case ATTEMPTING:
                     if (dist1 > 0.83) {
                        float yaw = RotationUtils.calcRotationFromVec3d(this.ctx.headPos(), VecUtils.getBlockPosCenter(this.dest), this.ctx.entityRotations())
                           .getYaw();
                        if (Math.abs(state.getTarget().rotation.getYaw() - yaw) < 0.1) {
                           return state.setInput(Input.MOVE_FORWARD, true);
                        }
                     } else if (this.ctx.entityRotations().isReallyCloseTo(state.getTarget().rotation)) {
                        return state.setInput(Input.CLICK_LEFT, true);
                     }

                     return state;
                  default:
                     if (feet.equals(this.dest)) {
                        double faceX = (this.dest.getX() + this.src.getX() + 1.0) * 0.5;
                        double faceY = (this.dest.getY() + this.src.getY() - 1.0) * 0.5;
                        double faceZ = (this.dest.getZ() + this.src.getZ() + 1.0) * 0.5;
                        BlockPos goalLook = this.src.down();
                        Rotation backToFace = RotationUtils.calcRotationFromVec3d(this.ctx.headPos(), new Vec3(faceX, faceY, faceZ), this.ctx.entityRotations());
                        float pitch = backToFace.getPitch();
                        double dist2 = Math.max(Math.abs(this.ctx.entity().getX() - faceX), Math.abs(this.ctx.entity().getZ() - faceZ));
                        if (dist2 < 0.29) {
                           float yaw = RotationUtils.calcRotationFromVec3d(
                                 VecUtils.getBlockPosCenter(this.dest), this.ctx.headPos(), this.ctx.entityRotations()
                              )
                              .getYaw();
                           state.setTarget(new MovementState.MovementTarget(new Rotation(yaw, pitch), true));
                           state.setInput(Input.MOVE_BACK, true);
                        } else {
                           state.setTarget(new MovementState.MovementTarget(backToFace, true));
                        }

                        if (this.ctx.isLookingAt(goalLook)) {
                           return state.setInput(Input.CLICK_RIGHT, true);
                        }

                        if (this.ctx.entityRotations().isReallyCloseTo(state.getTarget().rotation)) {
                           state.setInput(Input.CLICK_LEFT, true);
                        }

                        return state;
                     }

                     MovementHelper.moveTowards(this.ctx, state, this.dest.up());
               }
            }

            return state;
         }
      }
   }

   private boolean tryOpenDoors(MovementState state, BlockState bs, BetterBlockPos dest, BetterBlockPos src) {
      if (bs.getBlock() instanceof DoorBlock) {
         boolean notPassable = bs.getBlock() instanceof DoorBlock && !MovementHelper.isDoorPassable(this.ctx, dest, src);
         boolean canOpen = DoorBlock.isWoodenDoor(bs);
         if (notPassable && canOpen) {
            state.setTarget(
                  new MovementState.MovementTarget(
                     RotationUtils.calcRotationFromVec3d(
                        this.ctx.headPos(), VecUtils.calculateBlockCenter(this.ctx.world(), dest.up()), this.ctx.entityRotations()
                     ),
                     true
                  )
               )
               .setInput(Input.CLICK_RIGHT, true);
            return true;
         }
      } else if (bs.getBlock() instanceof FenceGateBlock) {
         BlockPos blocked = !MovementHelper.isGatePassable(this.ctx, dest.up(), src.up())
            ? dest.up()
            : (!MovementHelper.isGatePassable(this.ctx, dest, src) ? dest : null);
         if (blocked != null) {
            Optional<Rotation> rotation = RotationUtils.reachable(this.ctx, blocked);
            if (rotation.isPresent()) {
               state.setTarget(new MovementState.MovementTarget(rotation.get(), true)).setInput(Input.CLICK_RIGHT, true);
               return true;
            }
         }
      }

      return false;
   }

   @Override
   public boolean safeToCancel(MovementState state) {
      return state.getStatus() != MovementStatus.RUNNING || MovementHelper.canWalkOn(this.ctx, this.dest.down());
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
