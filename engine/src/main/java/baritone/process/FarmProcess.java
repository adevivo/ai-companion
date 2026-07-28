package baritone.process;

import baritone.PlayerEngine;
import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalBlock;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import org.jetbrains.annotations.Nullable;

public final class FarmProcess extends BaritoneProcessHelper implements IFarmProcess {
   private boolean active;
   private List<BlockPos> locations;
   private int tickCount;
   /** Throttle for the "nothing to do" diagnostic; reset whenever there is real work. */
   private int idleLogTicks;
   /** Ticks spent waiting for the async chunk scan to land. Non-zero means we are doing nothing. */
   private int scanPendingTicks;
   /** Throttle for the periodic "what the farm can see" progress line. */
   private int workLogTicks;
   /** @see #getStallReason() */
   private volatile String stallReason;
   private int range;
   private BlockPos center;

   // --- Row walk -------------------------------------------------------------------------------
   // Upstream works opportunistically: break everything currently in reach, then plant everything
   // currently in reach, driven by a GoalComposite of every candidate at once. That has two failure
   // modes we hit for real. A composite goal is satisfied when *any* member is satisfied, so standing
   // next to one incidental crop convinced the pathing layer it had arrived while the raytrace reach
   // check refused that same block — 122 mature crops, three minutes, not one step taken. And because
   // breaking and planting are separate sweeps over "whatever is nearby", a field could be stripped
   // without ever being sown.
   //
   // Instead: order the soil tiles into rows and work them one at a time, harvesting and then
   // immediately replanting each tile before moving to the next, with a single unambiguous goal.

   /** Soil positions for the current pass, in serpentine row order. Null until a pass is planned. */
   private List<BlockPos> tileOrder;
   /** Index into {@link #tileOrder}. */
   private int cursor;
   /** Ticks spent on the current tile; bounds how long one bad tile can hold up the field. */
   private int tileTicks;
   /** Whether a crop was broken on the current tile during this visit, so the replant is owed. */
   private boolean tileHarvested;
   private int passHarvested;
   private int passSown;
   private int passSkipped;
   /** Actions taken during the previous completed pass; zero means the field is fully tended. */
   private int lastPassActions = -1;
   /** Ticks spent walking to dropped crops between passes. */
   private int collectTicks;
   /** Countdown before re-planning a pass that found nothing to do. */
   private int idleReplanTicks;
   /** Whether we issued the replant click on the current tile, so the resulting crop is ours to count. */
   private boolean tilePlanting;

   /** Give up on a tile after this long (15s) and move on rather than blocking the whole field. */
   private static final int MAX_TILE_TICKS = 300;
   /** Cap on time spent gathering drops between passes, so it cannot become the whole job. */
   private static final int MAX_COLLECT_TICKS = 600;
   /** Ticks to wait before re-planning a pass that found nothing to do. */
   private static final int IDLE_REPLAN_TICKS = 100;
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

   /**
    * Arm the farm. Safe to call repeatedly with the same arguments — re-arming keeps the scan.
    *
    * <p>This used to clear {@link #locations} unconditionally, which made re-arming actively harmful:
    * {@link #onTick} does nothing at all while locations is null, and {@code FarmTask.onTick} re-arms
    * on any tick where the process is not active. Between them a single dropped tick could livelock the
    * pair — clear, pause, re-arm, clear — with the companion standing perfectly still while its task
    * reported {@code <Farming ...>}. Observed live on 2026-07-28: three farm runs, no movement, and not
    * one line of the "nothing to do" diagnostic, because that code was never reached.
    *
    * <p>Only a genuine change of target invalidates the scan, and that also resets {@link #tickCount} so
    * the replacement lands on the next tick rather than up to {@code mineGoalUpdateInterval} later.
    */
   @Override
   public void farm(int range, BlockPos pos) {
      BlockPos newCenter = pos == null ? this.baritone.getEntityContext().feetPos() : pos;
      boolean targetChanged = range != this.range || !newCenter.equals(this.center);
      this.center = newCenter;
      this.range = range;
      this.active = true;
      if (targetChanged) {
         this.locations = null;
         this.tickCount = 0;
         this.scanPendingTicks = 0;
         // The row walk belongs to the old field; keep none of it.
         this.tileOrder = null;
         this.cursor = 0;
         this.lastPassActions = -1;
         this.idleReplanTicks = 0;
         this.passHarvested = 0;
         this.passSown = 0;
         this.passSkipped = 0;
      }
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
         // An exception in here used to vanish without trace and leave locations null forever, which
         // presents in-world as the companion standing motionless while claiming to farm. If the scan
         // cannot run, say so — that is a very different problem from having nothing to harvest.
         // The old fixed cap of 256 results is what made the companion work an arbitrary slice of a
         // large field: WorldScanner stops expanding as soon as it has `max` hits, so the boundary was
         // never established and the returned set was chosen by chunk iteration order. A `farm 32`
         // covers 65x65, so size the budget to the area actually asked for. Still bounded — this drives
         // a synchronous chunk walk — but bounded by the request rather than by an arbitrary constant.
         int scanMax = this.scanBudget();
         PlayerEngine.getExecutor().execute(() -> {
            try {
               this.locations = WorldScanner.INSTANCE.scanChunkRadius(this.ctx, scan, scanMax, 10, 10);
            } catch (Throwable t) {
               this.stallReason = "the block scan failed (" + t + "), so I cannot see the field at all";
               this.logDirect("Farm scan failed: " + t + ". Cannot see the field, so nothing will be harvested or replanted.");
            }
         });
      }

      if (this.locations == null) {
         // Silent inactivity is the hardest farming symptom to diagnose from outside, so name it. The
         // first scan legitimately takes a moment; anything beyond that is a fault.
         if (this.scanPendingTicks++ % 200 == 20) {
            this.stallReason = "I am not actually farming — the block scan of the field has not come back, so I am standing still";
            this.logDirect(String.format(
                  "Farm waiting on its first block scan of range %d around %s — %d ticks so far, doing nothing until it lands.",
                  this.range, this.center, this.scanPendingTicks));
         }

         return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
      } else {
         this.scanPendingTicks = 0;
         this.baritone.getInputOverrideHandler().clearAllKeys();

         // A path that cannot be calculated is a fact about one tile, not about the field. Skipping is
         // what keeps a single awkward corner from stopping the whole job, which is precisely the class
         // of failure this rewrite exists to remove.
         if (calcFailed) {
            this.skipTile("no path to it");
         }

         if (this.tileOrder == null || this.cursor >= this.tileOrder.size()) {
            // A pass that changed nothing means the field is tended and we are waiting on growth. Say so
            // and idle, rather than re-planning a pointless pass every tick.
            if (this.lastPassActions == 0 && this.idleReplanTicks++ < IDLE_REPLAN_TICKS) {
               boolean hasSeeds = this.baritone.getInventoryBehavior().throwaway(false, this::isPlantable);
               this.stallReason = this.passSkipped > 0 && !hasSeeds
                     ? "the field is harvested but I have no seeds left, so some tiles are sitting empty"
                     : "the field is fully harvested and replanted — I am waiting for the crops to regrow";
               if (this.idleLogTicks++ % 200 == 0) {
                  this.logDirect(String.format(
                        "Farm idle: nothing left to do in range %d of %s — scanned=%d, tiles=%d, hasSeeds=%b."
                              + " Standing by for regrowth.",
                        this.range, this.center, this.locations.size(),
                        this.tileOrder == null ? 0 : this.tileOrder.size(), hasSeeds));
               }

               return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            this.idleReplanTicks = 0;
            PathingCommand collect = this.collectDroppedCrops();
            if (collect != null) {
               return collect;
            }

            this.planPass();
         }

         if (this.tileOrder.isEmpty()) {
            this.stallReason = "there is no farmland within " + this.range + " blocks of " + this.center
                  + ", so there is nothing here to tend";
            if (this.idleLogTicks++ % 200 == 0) {
               this.logDirect(String.format("Farm idle: no farmland found in range %d of %s (scanned=%d).",
                     this.range, this.center, this.locations.size()));
            }

            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
         }

         BlockPos soil = this.tileOrder.get(this.cursor);
         BlockPos cropPos = soil.above();
         BlockState soilState = this.ctx.world().getBlockState(soil);
         BlockState cropState = this.ctx.world().getBlockState(cropPos);
         boolean soulsand = soilState.getBlock() == Blocks.SOUL_SAND;
         boolean hasSeed = this.baritone.getInventoryBehavior().throwaway(false, soulsand ? this::isNetherWart : this::isPlantable);

         if (this.workLogTicks++ % 200 == 0) {
            this.logDirect(String.format(
                  "Farm working range %d of %s: tile %d/%d at %s, harvested=%d, sown=%d, skipped=%d, hasSeeds=%b.",
                  this.range, this.center, this.cursor + 1, this.tileOrder.size(), soil.toShortString(),
                  this.passHarvested, this.passSown, this.passSkipped, hasSeed));
         }

         // The soil went away under us (someone tilled it back to dirt, or the scan is stale).
         if (soilState.getBlock() != Blocks.FARMLAND && !soulsand) {
            this.advanceTile();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
         }

         if (this.tileTicks++ > MAX_TILE_TICKS) {
            this.skipTile(this.tileHarvested ? "could not reach it to replant" : "could not reach it");
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
         }

         // 1. Harvest. Stay on this tile until the crop is actually gone — advancing on the tick the
         //    click is issued would leave the tile bare and unsown, which is the very symptom we are
         //    fixing.
         if (this.readyForHarvest(this.ctx.world(), cropPos, cropState)) {
            Optional<Rotation> rot = RotationUtils.reachable(this.ctx, cropPos);
            if (rot.isPresent() && isSafeToCancel) {
               this.baritone.getLookBehavior().updateTarget(rot.get(), true);
               MovementHelper.switchToBestToolFor(this.ctx, cropState);
               if (this.ctx.isLookingAt(cropPos)) {
                  this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                  this.tileHarvested = true;
               }

               return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            // Stand ON the tile rather than merely near it. GoalBreak (and any GoalGetToBlock) is
            // satisfied by adjacency, which is exactly how the old composite goal convinced the pathing
            // layer it had arrived while the raytrace reach check kept refusing the block — a standoff
            // that never resolved. A GoalBlock on the tile itself is a single precise position that
            // cannot be satisfied by being near some other tile, and standing in the crop makes both the
            // harvest and the replant a straight look downwards.
            this.stallReason = null;
            return new PathingCommand(new GoalBlock(cropPos), PathingCommandType.SET_GOAL_AND_PATH);
         }

         if (this.tileHarvested) {
            // The click landed and the crop is gone: count it once, then fall through to replant it.
            this.passHarvested++;
            this.tileHarvested = false;
            this.tileTicks = 0;
         }

         // 2. Replant. Every tile we clear gets a seed back before we move on.
         if (cropState.getBlock() instanceof AirBlock) {
            if (!hasSeed) {
               this.skipTile(soulsand ? "out of nether wart" : "out of seeds");
               return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            Optional<Rotation> rot = RotationUtils.reachableOffset(
               this.ctx.entity(),
               soil,
               new Vec3(soil.getX() + 0.5, soil.getY() + 1, soil.getZ() + 0.5),
               this.ctx.playerController().getBlockReachDistance(),
               false
            );
            if (rot.isPresent()
               && isSafeToCancel
               && this.baritone.getInventoryBehavior().throwaway(true, soulsand ? this::isNetherWart : this::isPlantable)) {
               HitResult result = RayTraceUtils.rayTraceTowards(this.ctx.entity(), rot.get(), this.ctx.playerController().getBlockReachDistance());
               if (result instanceof BlockHitResult && ((BlockHitResult)result).getDirection() == Direction.UP) {
                  this.baritone.getLookBehavior().updateTarget(rot.get(), true);
                  if (this.ctx.isLookingAt(soil)) {
                     this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                     this.tilePlanting = true;
                  }

                  return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
               }
            }

            this.stallReason = null;
            return new PathingCommand(new GoalBlock(cropPos), PathingCommandType.SET_GOAL_AND_PATH);
         }

         // 3. Something is growing here. Bone-meal it if we can, then move on either way — a second
         //    application can wait for the next pass rather than parking us on one tile.
         if (cropState.getBlock() instanceof BonemealableBlock bonemealable
            && bonemealable.isValidBonemealTarget(this.ctx.world(), cropPos, cropState, true)
            && bonemealable.isBonemealSuccess(this.ctx.world(), this.ctx.world().random, cropPos, cropState)
            && this.baritone.getInventoryBehavior().throwaway(false, this::isBoneMeal)) {
            Optional<Rotation> rot = RotationUtils.reachable(this.ctx, cropPos);
            if (rot.isPresent() && isSafeToCancel && this.baritone.getInventoryBehavior().throwaway(true, this::isBoneMeal)) {
               this.baritone.getLookBehavior().updateTarget(rot.get(), true);
               if (this.ctx.isLookingAt(cropPos)) {
                  this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                  this.advanceTile();
               }

               return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
         }

         // Sown and growing, or occupied by something we do not manage. Either way this tile is done.
         // Only count a seed we actually placed — counting every growing crop we walk past would make
         // the pass summary claim work that never happened, which is the failure mode this whole
         // exercise has been about.
         if (this.tilePlanting) {
            this.passSown++;
         }

         this.advanceTile();
         return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
      }
   }

   /**
    * How many block positions the scan may return.
    *
    * <p>Sized to the area the owner actually asked for. A fixed cap truncates a real field and leaves
    * the boundary unknown, which is how the companion came to work only a corner of it.
    */
   private int scanBudget() {
      if (this.range <= 0) {
         return 16384;
      }

      long span = 2L * this.range + 1L;
      return (int)Math.max(512L, Math.min(16384L, span * span));
   }

   /**
    * Order every soil tile in range into a serpentine walk — rows along X, alternating direction — so
    * the field is covered predictably instead of by whichever tile happens to be nearest.
    */
   private void planPass() {
      if (this.lastPassActions >= 0) {
         this.logDirect(String.format("Farm pass complete over %d tiles: harvested=%d, sown=%d, skipped=%d.",
               this.tileOrder == null ? 0 : this.tileOrder.size(), this.passHarvested, this.passSown, this.passSkipped));
      }

      this.lastPassActions = this.passHarvested + this.passSown;
      this.passHarvested = 0;
      this.passSown = 0;
      this.passSkipped = 0;
      this.cursor = 0;
      this.tileTicks = 0;
      this.tileHarvested = false;
      this.tilePlanting = false;
      this.collectTicks = 0;

      Set<BlockPos> soils = new LinkedHashSet<>();
      for (BlockPos pos : this.locations) {
         BlockPos soil = this.ctx.world().getBlockState(pos).getBlock() == Blocks.FARMLAND
               || this.ctx.world().getBlockState(pos).getBlock() == Blocks.SOUL_SAND
            ? pos
            : pos.below();
         if (this.range == 0 || soil.distSqr(this.center) <= (long)this.range * this.range) {
            Block block = this.ctx.world().getBlockState(soil).getBlock();
            if (block == Blocks.FARMLAND || block == Blocks.SOUL_SAND) {
               soils.add(soil);
            }
         }
      }

      List<BlockPos> ordered = new ArrayList<>(soils);
      int minZ = ordered.stream().mapToInt(BlockPos::getZ).min().orElse(0);
      // Rows run along X and alternate direction, so the walk snakes across the field and each row
      // starts where the previous one ended instead of returning to the same edge every time.
      ordered.sort(
         Comparator.<BlockPos>comparingInt(p -> p.getY())
            .thenComparingInt(p -> p.getZ())
            .thenComparingInt(p -> Math.floorMod(p.getZ() - minZ, 2) == 0 ? p.getX() : -p.getX())
      );
      this.tileOrder = ordered;
   }

   /** Move to the next tile, clearing the per-tile state. */
   private void advanceTile() {
      this.cursor++;
      this.tileTicks = 0;
      this.tileHarvested = false;
      this.tilePlanting = false;
   }

   /** Abandon the current tile, recording why, so one bad tile cannot hold up the field. */
   private void skipTile(String why) {
      if (this.tileOrder != null && this.cursor < this.tileOrder.size()) {
         this.passSkipped++;
         this.stallReason = "I had to skip the tile at " + this.tileOrder.get(this.cursor).toShortString() + " — " + why;
      }

      this.advanceTile();
   }

   /**
    * Between passes, walk to any crop drops lying around rather than leaving them to despawn. Returns a
    * pathing command while there is something to fetch, or null when there is not.
    */
   private PathingCommand collectDroppedCrops() {
      if (this.collectTicks++ > MAX_COLLECT_TICKS) {
         return null;
      }

      ItemEntity nearest = null;
      double best = Double.MAX_VALUE;
      for (ItemEntity item : this.ctx.world().getEntitiesOfClass(ItemEntity.class, this.ctx.entity().getBoundingBox().inflate(30.0), Entity::onGround)) {
         if (PICKUP_DROPPED.contains(item.getItem().getItem())) {
            double d = item.distanceToSqr(this.ctx.entity());
            if (d < best) {
               best = d;
               nearest = item;
            }
         }
      }

      if (nearest == null) {
         return null;
      }

      this.stallReason = null;
      return new PathingCommand(
         new GoalBlock(BlockPos.containing(nearest.getX(), nearest.getY() + 0.1, nearest.getZ())),
         PathingCommandType.SET_GOAL_AND_PATH
      );
   }

   @Override
   @Nullable
   public String getStallReason() {
      return this.stallReason;
   }

   @Override
   public void onLostControl() {
      this.active = false;
      this.stallReason = null;
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
