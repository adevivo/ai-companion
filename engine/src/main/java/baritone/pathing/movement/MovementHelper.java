package baritone.pathing.movement;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.entity.IInventoryProvider;
import baritone.api.entity.LivingEntityInventory;
import baritone.api.pathing.movement.ActionCosts;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IEntityContext;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.VecUtils;
import baritone.api.utils.input.Input;
import baritone.utils.BlockStateInterface;
import baritone.utils.ToolSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public interface MovementHelper extends ActionCosts {
   static boolean avoidBreaking(BlockStateInterface bsi, int x, int y, int z, BlockState state, Settings settings) {
      Block b = state.getBlock();
      return b == Blocks.ICE
         || b instanceof InfestedBlock
         || avoidAdjacentBreaking(bsi, x, y + 1, z, true, settings)
         || avoidAdjacentBreaking(bsi, x + 1, y, z, false, settings)
         || avoidAdjacentBreaking(bsi, x - 1, y, z, false, settings)
         || avoidAdjacentBreaking(bsi, x, y, z + 1, false, settings)
         || avoidAdjacentBreaking(bsi, x, y, z - 1, false, settings);
   }

   static boolean avoidAdjacentBreaking(BlockStateInterface bsi, int x, int y, int z, boolean directlyAbove, Settings settings) {
      BlockState state = bsi.get0(x, y, z);
      Block block = state.getBlock();
      return !directlyAbove && block instanceof FallingBlock && settings.avoidUpdatingFallingBlocks.get() && FallingBlock.isFree(bsi.get0(x, y - 1, z))
         ? true
         : !state.getFluidState().isEmpty();
   }

   static boolean canWalkThrough(IEntityContext ctx, BetterBlockPos pos) {
      return canWalkThrough(new BlockStateInterface(ctx), pos.x, pos.y, pos.z, ctx.baritone().settings());
   }

   static boolean canWalkThrough(BlockStateInterface bsi, int x, int y, int z, Settings settings) {
      return canWalkThrough(bsi, x, y, z, bsi.get0(x, y, z), settings);
   }

   static boolean canWalkThrough(BlockStateInterface bsi, int x, int y, int z, BlockState state, Settings settings) {
      Block block = state.getBlock();
      if (block instanceof AirBlock) {
         return true;
      } else if (!(block instanceof BaseFireBlock)
         && block != Blocks.TRIPWIRE
         && block != Blocks.COBWEB
         && block != Blocks.END_PORTAL
         && block != Blocks.COCOA
         && !(block instanceof AbstractSkullBlock)
         && block != Blocks.BUBBLE_COLUMN
         && !(block instanceof ShulkerBoxBlock)
         && !(block instanceof SlabBlock)
         && !(block instanceof TrapDoorBlock)
         && block != Blocks.HONEY_BLOCK
         && block != Blocks.AZALEA
         && block != Blocks.FLOWERING_AZALEA
         && block != Blocks.GLOW_LICHEN
         && block != Blocks.CAVE_VINES
         && block != Blocks.CAVE_VINES_PLANT
         && block != Blocks.END_ROD) {
         if (settings.blocksToAvoid.get().contains(state.getBlock())) {
            return false;
         } else if (block instanceof DoorBlock || block instanceof FenceGateBlock) {
            return block instanceof FenceGateBlock || DoorBlock.isWoodenDoor(state);
         } else if (block instanceof CarpetBlock) {
            return canWalkOn(bsi, x, y - 1, z, settings);
         } else if (block instanceof SnowLayerBlock) {
            if (!bsi.worldContainsLoadedChunk(x, z)) {
               return true;
            } else {
               return state.getValue(SnowLayerBlock.LAYERS) >= 3 ? false : canWalkOn(bsi, x, y - 1, z, settings);
            }
         } else if (isFlowing(x, y, z, state, bsi)) {
            return false;
         } else {
            FluidState fluidState = state.getFluidState();
            if (!(fluidState.getType() instanceof WaterFluid)) {
               return state.isPathfindable(bsi.access, BlockPos.ZERO, PathComputationType.LAND);
            } else if (settings.assumeWalkOnWater.get()) {
               return false;
            } else {
               BlockState up = bsi.get0(x, y + 1, z);
               return (settings.allowSwimming.get() || up.getFluidState().isEmpty()) && !(up.getBlock() instanceof WaterlilyBlock);
            }
         }
      } else {
         return false;
      }
   }

   static boolean fullyPassable(CalculationContext context, int x, int y, int z) {
      return fullyPassable(context.bsi.access, context.bsi.isPassableBlockPos.set(x, y, z), context.bsi.get0(x, y, z));
   }

   static boolean fullyPassable(IEntityContext ctx, BlockPos pos) {
      return fullyPassable(ctx.world(), pos, ctx.world().getBlockState(pos));
   }

   static boolean fullyPassable(BlockGetter access, BlockPos pos, BlockState state) {
      Block block = state.getBlock();
      if (block instanceof AirBlock) {
         return true;
      } else {
         return !(block instanceof BaseFireBlock)
               && block != Blocks.TRIPWIRE
               && block != Blocks.COBWEB
               && block != Blocks.VINE
               && block != Blocks.LADDER
               && block != Blocks.COCOA
               && !(block instanceof DoorBlock)
               && !(block instanceof FenceGateBlock)
               && !(block instanceof SnowLayerBlock)
               && state.getFluidState().isEmpty()
               && !(block instanceof TrapDoorBlock)
               && !(block instanceof EndPortalBlock)
               && !(block instanceof SkullBlock)
               && !(block instanceof ShulkerBoxBlock)
            ? state.isPathfindable(access, pos, PathComputationType.LAND)
            : false;
      }
   }

   static boolean isReplaceable(int x, int y, int z, BlockState state, BlockStateInterface bsi) {
      Block block = state.getBlock();
      if (block instanceof AirBlock) {
         return true;
      } else if (block instanceof SnowLayerBlock) {
         return !bsi.worldContainsLoadedChunk(x, z) ? true : (Integer)state.getValue(SnowLayerBlock.LAYERS) == 1;
      } else {
         return block != Blocks.LARGE_FERN && block != Blocks.TALL_GRASS ? state.canBeReplaced() : true;
      }
   }

   @Deprecated
   static boolean isReplacable(int x, int y, int z, BlockState state, BlockStateInterface bsi) {
      return isReplaceable(x, y, z, state, bsi);
   }

   static boolean isDoorPassable(IEntityContext ctx, BlockPos doorPos, BlockPos playerPos) {
      if (playerPos.equals(doorPos)) {
         return false;
      } else {
         BlockState state = BlockStateInterface.get(ctx, doorPos);
         return !(state.getBlock() instanceof DoorBlock) ? true : isHorizontalBlockPassable(doorPos, state, playerPos, DoorBlock.OPEN);
      }
   }

   static boolean isGatePassable(IEntityContext ctx, BlockPos gatePos, BlockPos playerPos) {
      if (playerPos.equals(gatePos)) {
         return false;
      } else {
         BlockState state = BlockStateInterface.get(ctx, gatePos);
         return !(state.getBlock() instanceof FenceGateBlock) ? true : (Boolean)state.getValue(FenceGateBlock.OPEN);
      }
   }

   static boolean isHorizontalBlockPassable(BlockPos blockPos, BlockState blockState, BlockPos playerPos, BooleanProperty propertyOpen) {
      if (playerPos.equals(blockPos)) {
         return false;
      } else {
         Axis facing = ((Direction)blockState.getValue(HorizontalDirectionalBlock.FACING)).getAxis();
         boolean open = (Boolean)blockState.getValue(propertyOpen);
         Axis playerFacing;
         if (!playerPos.north().equals(blockPos) && !playerPos.south().equals(blockPos)) {
            if (!playerPos.east().equals(blockPos) && !playerPos.west().equals(blockPos)) {
               return true;
            }

            playerFacing = Axis.X;
         } else {
            playerFacing = Axis.Z;
         }

         return facing == playerFacing == open;
      }
   }

   static boolean avoidWalkingInto(BlockState state) {
      Block block = state.getBlock();
      return !state.getFluidState().isEmpty()
         || block == Blocks.MAGMA_BLOCK
         || block == Blocks.CACTUS
         || block instanceof BaseFireBlock
         || block == Blocks.END_PORTAL
         || block == Blocks.COBWEB
         || block == Blocks.BUBBLE_COLUMN;
   }

   static boolean canWalkOn(BlockStateInterface bsi, int x, int y, int z, BlockState state, Settings settings) {
      Block block = state.getBlock();
      if (block instanceof AirBlock || block == Blocks.MAGMA_BLOCK || block == Blocks.BUBBLE_COLUMN || block == Blocks.HONEY_BLOCK) {
         return false;
      } else if (isBlockNormalCube(state)) {
         return true;
      } else if (state.is(BlockTags.CLIMBABLE)) {
         return true;
      } else if (block == Blocks.FARMLAND || block == Blocks.DIRT_PATH) {
         return true;
      } else if (block == Blocks.ENDER_CHEST || block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
         return true;
      } else if (isWater(state)) {
         BlockState upState = bsi.get0(x, y + 1, z);
         Block up = upState.getBlock();
         if (up != Blocks.LILY_PAD && !(up instanceof CarpetBlock)) {
            return !isFlowing(x, y, z, state, bsi) && upState.getFluidState().getType() != Fluids.FLOWING_WATER
               ? isWater(upState) ^ settings.assumeWalkOnWater.get()
               : isWater(upState) && !settings.assumeWalkOnWater.get();
         } else {
            return true;
         }
      } else if (settings.assumeWalkOnLava.get() && isLava(state) && !isFlowing(x, y, z, state, bsi)) {
         return true;
      } else if (block == Blocks.GLASS || block instanceof StainedGlassBlock) {
         return true;
      } else if (!(block instanceof SlabBlock)) {
         return block instanceof StairBlock;
      } else {
         return !settings.allowWalkOnBottomSlab.get() ? state.getValue(SlabBlock.TYPE) != SlabType.BOTTOM : true;
      }
   }

   static boolean canWalkOn(IEntityContext ctx, BetterBlockPos pos, BlockState state) {
      return canWalkOn(new BlockStateInterface(ctx), pos.x, pos.y, pos.z, state, ctx.baritone().settings());
   }

   static boolean canWalkOn(IEntityContext ctx, BlockPos pos) {
      return canWalkOn(new BlockStateInterface(ctx), pos.getX(), pos.getY(), pos.getZ(), ctx.baritone().settings());
   }

   static boolean canWalkOn(IEntityContext ctx, BetterBlockPos pos) {
      return canWalkOn(new BlockStateInterface(ctx), pos.x, pos.y, pos.z, ctx.baritone().settings());
   }

   static boolean canWalkOn(BlockStateInterface bsi, int x, int y, int z, Settings settings) {
      return canWalkOn(bsi, x, y, z, bsi.get0(x, y, z), settings);
   }

   static boolean canPlaceAgainst(BlockStateInterface bsi, int x, int y, int z) {
      return canPlaceAgainst(bsi, x, y, z, bsi.get0(x, y, z));
   }

   static boolean canPlaceAgainst(BlockStateInterface bsi, BlockPos pos) {
      return canPlaceAgainst(bsi, pos.getX(), pos.getY(), pos.getZ());
   }

   static boolean canPlaceAgainst(IEntityContext ctx, BlockPos pos) {
      return canPlaceAgainst(new BlockStateInterface(ctx), pos);
   }

   static boolean canPlaceAgainst(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
      return isBlockNormalCube(state) || state.getBlock() == Blocks.GLASS || state.getBlock() instanceof StainedGlassBlock;
   }

   static double getMiningDurationTicks(CalculationContext context, int x, int y, int z, boolean includeFalling) {
      return getMiningDurationTicks(context, x, y, z, context.get(x, y, z), includeFalling);
   }

   static double getMiningDurationTicks(CalculationContext context, int x, int y, int z, BlockState state, boolean includeFalling) {
      if (!canWalkThrough(context.bsi, x, y, z, state, context.baritone.settings())) {
         if (!state.getFluidState().isEmpty()) {
            return 1000000.0;
         } else {
            double mult = context.breakCostMultiplierAt(x, y, z, state);
            if (mult >= 1000000.0) {
               return 1000000.0;
            } else if (avoidBreaking(context.bsi, x, y, z, state, context.baritone.settings())) {
               return 1000000.0;
            } else if (context.toolSet == null) {
               return 1000000.0;
            } else {
               double strVsBlock = context.toolSet.getStrVsBlock(state);
               if (strVsBlock <= 0.0) {
                  return 1000000.0;
               } else {
                  double result = 1.0 / strVsBlock;
                  result += context.breakBlockAdditionalCost;
                  result *= mult;
                  if (includeFalling) {
                     BlockState above = context.get(x, y + 1, z);
                     if (above.getBlock() instanceof FallingBlock) {
                        result += getMiningDurationTicks(context, x, y + 1, z, above, true);
                     }
                  }

                  return result;
               }
            }
         }
      } else {
         return 0.0;
      }
   }

   static boolean isBottomSlab(BlockState state) {
      return state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
   }

   static void switchToBestToolFor(IEntityContext ctx, BlockState b) {
      LivingEntity entity = ctx.entity();
      if (entity instanceof IInventoryProvider) {
         switchToBestToolFor(ctx, b, new ToolSet(entity), ctx.baritone().settings().preferSilkTouch.get());
      }
   }

   static void switchToBestToolFor(IEntityContext ctx, BlockState b, ToolSet ts, boolean preferSilkTouch) {
      LivingEntityInventory inventory = ctx.inventory();
      if (inventory != null && !ctx.baritone().settings().disableAutoTool.get() && !ctx.baritone().settings().assumeExternalAutoTool.get()) {
         inventory.selectedSlot = ts.getBestSlot(b.getBlock(), preferSilkTouch);
      }
   }

   static void moveTowards(IEntityContext ctx, MovementState state, BlockPos pos) {
      state.setTarget(
            new MovementState.MovementTarget(
               new Rotation(
                  RotationUtils.calcRotationFromVec3d(ctx.headPos(), VecUtils.getBlockPosCenter(pos), ctx.entityRotations()).getYaw(), ctx.entity().getXRot()
               ),
               false
            )
         )
         .setInput(Input.MOVE_FORWARD, true);
   }

   static boolean isWater(BlockState state) {
      return state.getFluidState().is(FluidTags.WATER);
   }

   static boolean isWater(IEntityContext ctx, BlockPos bp) {
      return isWater(BlockStateInterface.get(ctx, bp));
   }

   static boolean isLava(BlockState state) {
      Fluid f = state.getFluidState().getType();
      return f == Fluids.LAVA || f == Fluids.FLOWING_LAVA;
   }

   static boolean isLiquid(IEntityContext ctx, BlockPos p) {
      return isLiquid(BlockStateInterface.get(ctx, p));
   }

   static boolean isLiquid(BlockState blockState) {
      return !blockState.getFluidState().isEmpty();
   }

   static boolean possiblyFlowing(BlockState state) {
      FluidState fluidState = state.getFluidState();
      return fluidState.getType() instanceof FlowingFluid && fluidState.getType().getAmount(fluidState) != 8;
   }

   static boolean isFlowing(int x, int y, int z, BlockState state, BlockStateInterface bsi) {
      FluidState fluidState = state.getFluidState();
      if (!(fluidState.getType() instanceof FlowingFluid)) {
         return false;
      } else {
         return fluidState.getType().getAmount(fluidState) != 8
            ? true
            : possiblyFlowing(bsi.get0(x + 1, y, z))
               || possiblyFlowing(bsi.get0(x - 1, y, z))
               || possiblyFlowing(bsi.get0(x, y, z + 1))
               || possiblyFlowing(bsi.get0(x, y, z - 1));
      }
   }

   static boolean isBlockNormalCube(BlockState state) {
      Block block = state.getBlock();
      if (!(block instanceof BambooStalkBlock)
         && !(block instanceof MovingPistonBlock)
         && !(block instanceof ScaffoldingBlock)
         && !(block instanceof ShulkerBoxBlock)) {
         try {
            return state.isCollisionShapeFullBlock(null, BlockPos.ZERO);
         } catch (NullPointerException var3) {
            return false;
         }
      } else {
         return false;
      }
   }

   static MovementHelper.PlaceResult attemptToPlaceABlock(MovementState state, IBaritone baritone, BlockPos placeAt, boolean preferDown, boolean wouldSneak) {
      IEntityContext ctx = baritone.getEntityContext();
      Optional<Rotation> direct = RotationUtils.reachable(ctx, placeAt, wouldSneak);
      boolean found = false;
      if (direct.isPresent()) {
         state.setTarget(new MovementState.MovementTarget(direct.get(), true));
         found = true;
      }

      int i = 0;

      while (true) {
         label81: {
            if (i < 5) {
               BlockPos against1 = placeAt.relative(Movement.HORIZONTALS_BUT_ALSO_DOWN_____SO_EVERY_DIRECTION_EXCEPT_UP[i]);
               if (!canPlaceAgainst(ctx, against1)) {
                  break label81;
               }

               if (!((Baritone)baritone).getInventoryBehavior().selectThrowawayForLocation(false, placeAt.getX(), placeAt.getY(), placeAt.getZ())) {
                  baritone.logDebug("bb pls get me some blocks. dirt, netherrack, cobble");
                  state.setStatus(MovementStatus.UNREACHABLE);
                  return MovementHelper.PlaceResult.NO_OPTION;
               }

               double faceX = (placeAt.getX() + against1.getX() + 1.0) * 0.5;
               double faceY = (placeAt.getY() + against1.getY() + 0.5) * 0.5;
               double faceZ = (placeAt.getZ() + against1.getZ() + 1.0) * 0.5;
               Rotation place = RotationUtils.calcRotationFromVec3d(
                  wouldSneak ? RayTraceUtils.inferSneakingEyePosition(ctx.entity()) : ctx.headPos(), new Vec3(faceX, faceY, faceZ), ctx.entityRotations()
               );
               HitResult res = RayTraceUtils.rayTraceTowards(ctx.entity(), place, ctx.playerController().getBlockReachDistance(), wouldSneak);
               if (res == null
                  || res.getType() != Type.BLOCK
                  || !((BlockHitResult)res).getBlockPos().equals(against1)
                  || !((BlockHitResult)res).getBlockPos().relative(((BlockHitResult)res).getDirection()).equals(placeAt)) {
                  break label81;
               }

               state.setTarget(new MovementState.MovementTarget(place, true));
               found = true;
               if (preferDown) {
                  break label81;
               }
            }

            if (ctx.getSelectedBlock().isPresent()) {
               BlockPos selectedBlock = ctx.getSelectedBlock().get();
               Direction side = ((BlockHitResult)ctx.objectMouseOver()).getDirection();
               if (selectedBlock.equals(placeAt) || canPlaceAgainst(ctx, selectedBlock) && selectedBlock.relative(side).equals(placeAt)) {
                  if (wouldSneak) {
                     state.setInput(Input.SNEAK, true);
                  }

                  ((Baritone)baritone).getInventoryBehavior().selectThrowawayForLocation(true, placeAt.getX(), placeAt.getY(), placeAt.getZ());
                  return MovementHelper.PlaceResult.READY_TO_PLACE;
               }
            }

            if (found) {
               if (wouldSneak) {
                  state.setInput(Input.SNEAK, true);
               }

               ((Baritone)baritone).getInventoryBehavior().selectThrowawayForLocation(true, placeAt.getX(), placeAt.getY(), placeAt.getZ());
               return MovementHelper.PlaceResult.ATTEMPTING;
            }

            return MovementHelper.PlaceResult.NO_OPTION;
         }

         i++;
      }
   }

   static boolean isTransparent(Block b) {
      return b == Blocks.AIR || b == Blocks.LAVA || b == Blocks.WATER;
   }

   public static enum PlaceResult {
      READY_TO_PLACE,
      ATTEMPTING,
      NO_OPTION;
   }
}
