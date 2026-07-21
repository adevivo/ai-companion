package baritone.process;

import baritone.PlayerEngine;
import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.process.IFarmProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.cache.WorldScanner;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import baritone.utils.NotificationHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class FarmProcess extends BaritoneProcessHelper implements IFarmProcess {
   private boolean active;
   private List<BlockPos> locations;
   private int tickCount;
   private int range;
   private BlockPos center;
   private static final List<Item> FARMLAND_PLANTABLE = Arrays.asList(
      Items.BEETROOT_SEEDS, Items.MELON_SEEDS, Items.WHEAT_SEEDS, Items.PUMPKIN_SEEDS, Items.POTATO, Items.CARROT
   );
   private static final List<Item> PICKUP_DROPPED = Arrays.asList(
      Items.BEETROOT_SEEDS,
      Items.BEETROOT,
      Items.MELON_SEEDS,
      Items.MELON_SLICE,
      Blocks.MELON.asItem(),
      Items.WHEAT_SEEDS,
      Items.WHEAT,
      Items.PUMPKIN_SEEDS,
      Blocks.PUMPKIN.asItem(),
      Items.POTATO,
      Items.CARROT,
      Items.NETHER_WART,
      Blocks.SUGAR_CANE.asItem(),
      Blocks.CACTUS.asItem()
   );

   public FarmProcess(Baritone baritone) {
      super(baritone);
   }

   @Override
   public boolean isActive() {
      return this.active;
   }

   @Override
   public void farm(int range, BlockPos pos) {
      if (pos == null) {
         this.center = this.baritone.getEntityContext().feetPos();
      } else {
         this.center = pos;
      }

      this.range = range;
      this.active = true;
      this.locations = null;
   }

   private boolean readyForHarvest(Level world, BlockPos pos, BlockState state) {
      for (FarmProcess.Harvest harvest : FarmProcess.Harvest.values()) {
         if (harvest.block == state.getBlock()) {
            return harvest.readyToHarvest(world, pos, state, this.baritone.settings());
         }
      }

      return false;
   }

   private boolean isPlantable(ItemStack stack) {
      return FARMLAND_PLANTABLE.contains(stack.getItem());
   }

   private boolean isBoneMeal(ItemStack stack) {
      return !stack.isEmpty() && stack.getItem().equals(Items.BONE_MEAL);
   }

   private boolean isNetherWart(ItemStack stack) {
      return !stack.isEmpty() && stack.getItem().equals(Items.NETHER_WART);
   }

   @Override
   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      ArrayList<Block> scan = new ArrayList<>();

      for (FarmProcess.Harvest harvest : FarmProcess.Harvest.values()) {
         scan.add(harvest.block);
      }

      if (this.baritone.settings().replantCrops.get()) {
         scan.add(Blocks.FARMLAND);
         if (this.baritone.settings().replantNetherWart.get()) {
            scan.add(Blocks.SOUL_SAND);
         }
      }

      if (this.baritone.settings().mineGoalUpdateInterval.get() != 0 && this.tickCount++ % this.baritone.settings().mineGoalUpdateInterval.get() == 0) {
         PlayerEngine.getExecutor().execute(() -> this.locations = WorldScanner.INSTANCE.scanChunkRadius(this.ctx, scan, 256, 10, 10));
      }

      if (this.locations == null) {
         return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
      } else {
         List<BlockPos> toBreak = new ArrayList<>();
         List<BlockPos> openFarmland = new ArrayList<>();
         List<BlockPos> bonemealable = new ArrayList<>();
         List<BlockPos> openSoulsand = new ArrayList<>();

         for (BlockPos pos : this.locations) {
            if (this.range == 0 || !(pos.distSqr(this.center) > this.range * this.range)) {
               BlockState state = this.ctx.world().getBlockState(pos);
               boolean airAbove = this.ctx.world().getBlockState(pos.above()).getBlock() instanceof AirBlock;
               if (state.getBlock() == Blocks.FARMLAND) {
                  if (airAbove) {
                     openFarmland.add(pos);
                  }
               } else if (state.getBlock() == Blocks.SOUL_SAND) {
                  if (airAbove) {
                     openSoulsand.add(pos);
                  }
               } else if (this.readyForHarvest(this.ctx.world(), pos, state)) {
                  toBreak.add(pos);
               } else if (state.getBlock() instanceof BonemealableBlock) {
                  BonemealableBlock ig = (BonemealableBlock)state.getBlock();
                  if (ig.isValidBonemealTarget(this.ctx.world(), pos, state, true)
                     && ig.isBonemealSuccess(this.ctx.world(), this.ctx.world().random, pos, state)) {
                     bonemealable.add(pos);
                  }
               }
            }
         }

         this.baritone.getInputOverrideHandler().clearAllKeys();

         for (BlockPos posx : toBreak) {
            Optional<Rotation> rot = RotationUtils.reachable(this.ctx, posx);
            if (rot.isPresent() && isSafeToCancel) {
               this.baritone.getLookBehavior().updateTarget(rot.get(), true);
               MovementHelper.switchToBestToolFor(this.ctx, this.ctx.world().getBlockState(posx));
               if (this.ctx.isLookingAt(posx)) {
                  this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
               }

               return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
         }

         ArrayList<BlockPos> both = new ArrayList<>(openFarmland);
         both.addAll(openSoulsand);

         for (BlockPos posxx : both) {
            boolean soulsand = openSoulsand.contains(posxx);
            Optional<Rotation> rot = RotationUtils.reachableOffset(
               this.ctx.entity(),
               posxx,
               new Vec3(posxx.getX() + 0.5, posxx.getY() + 1, posxx.getZ() + 0.5),
               this.ctx.playerController().getBlockReachDistance(),
               false
            );
            if (rot.isPresent() && isSafeToCancel && this.baritone.getInventoryBehavior().throwaway(true, soulsand ? this::isNetherWart : this::isPlantable)) {
               HitResult result = RayTraceUtils.rayTraceTowards(this.ctx.entity(), rot.get(), this.ctx.playerController().getBlockReachDistance());
               if (result instanceof BlockHitResult && ((BlockHitResult)result).getDirection() == Direction.UP) {
                  this.baritone.getLookBehavior().updateTarget(rot.get(), true);
                  if (this.ctx.isLookingAt(posxx)) {
                     this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                  }

                  return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
               }
            }
         }

         for (BlockPos posxxx : bonemealable) {
            Optional<Rotation> rot = RotationUtils.reachable(this.ctx, posxxx);
            if (rot.isPresent() && isSafeToCancel && this.baritone.getInventoryBehavior().throwaway(true, this::isBoneMeal)) {
               this.baritone.getLookBehavior().updateTarget(rot.get(), true);
               if (this.ctx.isLookingAt(posxxx)) {
                  this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
               }

               return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
         }

         if (calcFailed) {
            this.logDirect("Farm failed");
            if (this.baritone.settings().desktopNotifications.get() && this.baritone.settings().notificationOnFarmFail.get()) {
               NotificationHelper.notify("Farm failed", true);
            }

            this.onLostControl();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
         } else {
            List<Goal> goalz = new ArrayList<>();

            for (BlockPos posxxxx : toBreak) {
               goalz.add(new BuilderProcess.GoalBreak(posxxxx));
            }

            if (this.baritone.getInventoryBehavior().throwaway(false, this::isPlantable)) {
               for (BlockPos posxxxx : openFarmland) {
                  goalz.add(new GoalBlock(posxxxx.above()));
               }
            }

            if (this.baritone.getInventoryBehavior().throwaway(false, this::isNetherWart)) {
               for (BlockPos posxxxx : openSoulsand) {
                  goalz.add(new GoalBlock(posxxxx.above()));
               }
            }

            if (this.baritone.getInventoryBehavior().throwaway(false, this::isBoneMeal)) {
               for (BlockPos posxxxx : bonemealable) {
                  goalz.add(new GoalBlock(posxxxx));
               }
            }

            for (ItemEntity item : this.ctx.world().getEntitiesOfClass(ItemEntity.class, this.ctx.entity().getBoundingBox().inflate(30.0), Entity::onGround)) {
               if (PICKUP_DROPPED.contains(item.getItem().getItem())) {
                  goalz.add(new GoalBlock(BlockPos.containing(item.getX(), item.getY() + 0.1, item.getZ())));
               }
            }

            return goalz.isEmpty()
               ? new PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
               : new PathingCommand(new GoalComposite(goalz.toArray(new Goal[0])), PathingCommandType.SET_GOAL_AND_PATH);
         }
      }
   }

   @Override
   public void onLostControl() {
      this.active = false;
   }

   @Override
   public String displayName0() {
      return "Farming";
   }

   private static enum Harvest {
      WHEAT((CropBlock)Blocks.WHEAT),
      CARROTS((CropBlock)Blocks.CARROTS),
      POTATOES((CropBlock)Blocks.POTATOES),
      BEETROOT((CropBlock)Blocks.BEETROOTS),
      PUMPKIN(Blocks.PUMPKIN, state -> true),
      MELON(Blocks.MELON, state -> true),
      NETHERWART(Blocks.NETHER_WART, state -> (Integer)state.getValue(NetherWartBlock.AGE) >= 3),
      SUGARCANE(Blocks.SUGAR_CANE, null) {
         @Override
         public boolean readyToHarvest(Level world, BlockPos pos, BlockState state, Settings settings) {
            return settings.replantCrops.get() ? world.getBlockState(pos.below()).getBlock() instanceof SugarCaneBlock : true;
         }
      },
      CACTUS(Blocks.CACTUS, null) {
         @Override
         public boolean readyToHarvest(Level world, BlockPos pos, BlockState state, Settings settings) {
            return settings.replantCrops.get() ? world.getBlockState(pos.below()).getBlock() instanceof CactusBlock : true;
         }
      };

      public final Block block;
      public final Predicate<BlockState> readyToHarvest;

      private Harvest(CropBlock blockCrops) {
         this(blockCrops, blockCrops::isMaxAge);
      }

      private Harvest(Block block, Predicate<BlockState> readyToHarvest) {
         this.block = block;
         this.readyToHarvest = readyToHarvest;
      }

      public boolean readyToHarvest(Level world, BlockPos pos, BlockState state, Settings settings) {
         return this.readyToHarvest.test(state);
      }
   }
}
