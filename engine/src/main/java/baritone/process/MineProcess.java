package baritone.process;

import baritone.PlayerEngine;
import baritone.Baritone;
import baritone.api.entity.LivingEntityInventory;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalRunAway;
import baritone.api.pathing.goals.GoalTwoBlocks;
import baritone.api.process.IMineProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.BlockUtils;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.cache.CachedChunk;
import baritone.cache.WorldScanner;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.MovementHelper;
import baritone.utils.BaritoneProcessHelper;
import baritone.utils.BlockStateInterface;
import baritone.utils.NotificationHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class MineProcess extends BaritoneProcessHelper implements IMineProcess {
   private static final int ORE_LOCATIONS_COUNT = 64;
   private BlockOptionalMetaLookup filter;
   private List<BlockPos> knownOreLocations;
   private List<BlockPos> blacklist;
   private Map<BlockPos, Long> anticipatedDrops;
   private BlockPos branchPoint;
   private GoalRunAway branchPointRunaway;
   private int desiredQuantity;
   private int tickCount;

   public MineProcess(Baritone baritone) {
      super(baritone);
   }

   @Override
   public boolean isActive() {
      return this.filter != null;
   }

   @Override
   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      if (this.desiredQuantity > 0) {
         LivingEntityInventory inventory = this.ctx.inventory();
         int curr = inventory == null ? -1 : inventory.main.stream().filter(stack -> this.filter.has(stack)).mapToInt(ItemStack::getCount).sum();
         PlayerEngine.LOGGER.debug("Currently have " + curr + " valid items");
         if (curr >= this.desiredQuantity) {
            this.logDirect("Have " + curr + " valid items");
            this.cancel();
            return null;
         }
      }

      if (calcFailed) {
         if (this.knownOreLocations.isEmpty() || !this.baritone.settings().blacklistClosestOnFailure.get()) {
            this.logDirect("Unable to find any path to " + this.filter + ", canceling mine");
            if (this.baritone.settings().desktopNotifications.get() && this.baritone.settings().notificationOnMineFail.get()) {
               NotificationHelper.notify("Unable to find any path to " + this.filter + ", canceling mine", true);
            }

            this.cancel();
            return null;
         }

         this.logDirect("Unable to find any path to " + this.filter + ", blacklisting presumably unreachable closest instance...");
         if (this.baritone.settings().desktopNotifications.get() && this.baritone.settings().notificationOnMineFail.get()) {
            NotificationHelper.notify("Unable to find any path to " + this.filter + ", blacklisting presumably unreachable closest instance...", true);
         }

         this.knownOreLocations.stream().min(Comparator.comparingDouble(this.ctx.feetPos()::distSqr)).ifPresent(this.blacklist::add);
         this.knownOreLocations.removeIf(this.blacklist::contains);
      }

      if (!this.baritone.settings().allowBreak.get()) {
         this.logDirect("Unable to mine when allowBreak is false!");
         this.cancel();
         return null;
      } else {
         this.updateLoucaSystem();
         int mineGoalUpdateInterval = this.baritone.settings().mineGoalUpdateInterval.get();
         List<BlockPos> curr = new ArrayList<>(this.knownOreLocations);
         if (mineGoalUpdateInterval != 0 && this.tickCount++ % mineGoalUpdateInterval == 0) {
            CalculationContext context = new CalculationContext(this.baritone, true);
            PlayerEngine.getExecutor().execute(() -> this.rescan(curr, context));
         }

         if (this.baritone.settings().legitMine.get()) {
            this.addNearby();
         }

         Optional<BlockPos> shaft = curr.stream()
            .filter(pos -> pos.getX() == this.ctx.feetPos().getX() && pos.getZ() == this.ctx.feetPos().getZ())
            .filter(pos -> pos.getY() >= this.ctx.feetPos().getY())
            .filter(pos -> !(BlockStateInterface.get(this.ctx, pos).getBlock() instanceof AirBlock))
            .min(Comparator.comparingDouble(this.ctx.feetPos()::distSqr));
         this.baritone.getInputOverrideHandler().clearAllKeys();
         if (shaft.isPresent() && this.ctx.entity().onGround()) {
            BlockPos pos = shaft.get();
            BlockState state = this.baritone.bsi.get0(pos);
            if (!MovementHelper.avoidBreaking(this.baritone.bsi, pos.getX(), pos.getY(), pos.getZ(), state, this.baritone.settings())) {
               Optional<Rotation> rot = RotationUtils.reachable(this.ctx, pos);
               if (rot.isPresent() && isSafeToCancel) {
                  this.baritone.getLookBehavior().updateTarget(rot.get(), true);
                  MovementHelper.switchToBestToolFor(this.ctx, this.ctx.world().getBlockState(pos));
                  if (this.ctx.isLookingAt(pos) || this.ctx.entityRotations().isReallyCloseTo(rot.get())) {
                     this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                  }

                  return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
               }
            }
         }

         PathingCommand command = this.updateGoal();
         if (command == null) {
            this.cancel();
            return null;
         } else {
            return command;
         }
      }
   }

   private void updateLoucaSystem() {
      Map<BlockPos, Long> copy = new HashMap<>(this.anticipatedDrops);
      this.ctx.getSelectedBlock().ifPresent(posx -> {
         if (this.knownOreLocations.contains(posx)) {
            copy.put(posx, System.currentTimeMillis() + this.baritone.settings().mineDropLoiterDurationMSThanksLouca.get());
         }
      });

      for (BlockPos pos : this.anticipatedDrops.keySet()) {
         if (copy.get(pos) < System.currentTimeMillis()) {
            copy.remove(pos);
         }
      }

      this.anticipatedDrops = copy;
   }

   @Override
   public void onLostControl() {
      this.mine(0, (BlockOptionalMetaLookup)null);
   }

   @Override
   public String displayName0() {
      return "Mine " + this.filter;
   }

   private PathingCommand updateGoal() {
      boolean legit = this.baritone.settings().legitMine.get();
      List<BlockPos> locs = this.knownOreLocations;
      if (locs.isEmpty()) {
         if (!legit) {
            return null;
         } else {
            int y = this.baritone.settings().legitMineYLevel.get();
            if (this.branchPoint == null) {
               this.branchPoint = this.ctx.feetPos();
            }

            if (this.branchPointRunaway == null) {
               this.branchPointRunaway = new GoalRunAway(1.0, y, this.branchPoint) {
                  @Override
                  public boolean isInGoal(int x, int yx, int z) {
                     return false;
                  }

                  @Override
                  public double heuristic() {
                     return Double.NEGATIVE_INFINITY;
                  }
               };
            }

            return new PathingCommand(this.branchPointRunaway, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
         }
      } else {
         CalculationContext context = new CalculationContext(this.baritone);
         locs = prune(context, new ArrayList<>(locs), this.filter, 64, this.blacklist, this.droppedItemsScan());
         int locsSize = locs.size();
         Goal[] list = new Goal[locsSize];

         for (int i = 0; i < locsSize; i++) {
            BlockPos loc = locs.get(i);
            Goal coalesce = this.coalesce(loc, locs, context);
            list[i] = coalesce;
         }

         Goal goal = new GoalComposite(list);
         this.knownOreLocations = locs;
         return new PathingCommand(goal, legit ? PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH : PathingCommandType.REVALIDATE_GOAL_AND_PATH);
      }
   }

   private void rescan(List<BlockPos> already, CalculationContext context) {
      if (this.filter != null) {
         if (!this.baritone.settings().legitMine.get()) {
            List<BlockPos> dropped = this.droppedItemsScan();
            List<BlockPos> locs = searchWorld(context, this.filter, 64, already, this.blacklist, dropped);
            locs.addAll(dropped);
            if (locs.isEmpty()) {
               this.logDirect("No locations for " + this.filter + " known, cancelling");
               if (this.baritone.settings().desktopNotifications.get() && this.baritone.settings().notificationOnMineFail.get()) {
                  NotificationHelper.notify("No locations for " + this.filter + " known, cancelling", true);
               }

               this.cancel();
            } else {
               this.knownOreLocations = locs;
            }
         }
      }
   }

   private boolean internalMiningGoal(BlockPos pos, CalculationContext context, List<BlockPos> locs) {
      if (locs.contains(pos)) {
         return true;
      } else {
         BlockState state = context.bsi.get0(pos);
         return this.baritone.settings().internalMiningAirException.get() && state.getBlock() instanceof AirBlock
            ? true
            : this.filter.has(state) && plausibleToBreak(context, pos);
      }
   }

   private Goal coalesce(BlockPos loc, List<BlockPos> locs, CalculationContext context) {
      boolean assumeVerticalShaftMine = !(this.baritone.bsi.get0(loc.above()).getBlock() instanceof FallingBlock);
      if (!this.baritone.settings().forceInternalMining.get()) {
         return (Goal)(assumeVerticalShaftMine ? new MineProcess.GoalThreeBlocks(loc) : new GoalTwoBlocks(loc));
      } else {
         boolean upwardGoal = this.internalMiningGoal(loc.above(), context, locs);
         boolean downwardGoal = this.internalMiningGoal(loc.below(), context, locs);
         boolean doubleDownwardGoal = this.internalMiningGoal(loc.below(2), context, locs);
         if (upwardGoal == downwardGoal) {
            return (Goal)(doubleDownwardGoal && assumeVerticalShaftMine ? new MineProcess.GoalThreeBlocks(loc) : new GoalTwoBlocks(loc));
         } else if (upwardGoal) {
            return new GoalBlock(loc);
         } else {
            return (Goal)(doubleDownwardGoal && assumeVerticalShaftMine ? new GoalTwoBlocks(loc.below()) : new GoalBlock(loc.below()));
         }
      }
   }

   public List<BlockPos> droppedItemsScan() {
      if (!this.baritone.settings().mineScanDroppedItems.get()) {
         return Collections.emptyList();
      } else {
         List<BlockPos> ret = new ArrayList<>();

         for (Entity entity : this.ctx.world().getAllEntities()) {
            if (entity instanceof ItemEntity ei && this.filter.has(ei.getItem())) {
               ret.add(entity.blockPosition());
            }
         }

         ret.addAll(this.anticipatedDrops.keySet());
         return ret;
      }
   }

   public static List<BlockPos> searchWorld(
      CalculationContext ctx, BlockOptionalMetaLookup filter, int max, List<BlockPos> alreadyKnown, List<BlockPos> blacklist, List<BlockPos> dropped
   ) {
      List<BlockPos> locs = new ArrayList<>();
      List<Block> untracked = new ArrayList<>();

      for (BlockOptionalMeta bom : filter.blocks()) {
         Block block = bom.getBlock();
         if (CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(block)) {
            BetterBlockPos pf = ctx.baritone.getEntityContext().feetPos();
            locs.addAll(
               ctx.worldData
                  .getCachedWorld()
                  .getLocationsOf(BlockUtils.blockToString(block), ctx.baritone.settings().maxCachedWorldScanCount.get(), pf.x, pf.z, 2)
            );
         } else {
            untracked.add(block);
         }
      }

      locs = prune(ctx, locs, filter, max, blacklist, dropped);
      if (!untracked.isEmpty() || ctx.baritone.settings().extendCacheOnThreshold.get() && locs.size() < max) {
         locs.addAll(WorldScanner.INSTANCE.scanChunkRadius(ctx.getBaritone().getEntityContext(), filter, max, 10, 32));
      }

      locs.addAll(alreadyKnown);
      return prune(ctx, locs, filter, max, blacklist, dropped);
   }

   private void addNearby() {
      List<BlockPos> dropped = this.droppedItemsScan();
      this.knownOreLocations.addAll(dropped);
      BlockPos playerFeet = this.ctx.feetPos();
      BlockStateInterface bsi = new BlockStateInterface(this.ctx);
      int searchDist = 10;
      double fakedBlockReachDistance = 20.0;

      for (int x = playerFeet.getX() - searchDist; x <= playerFeet.getX() + searchDist; x++) {
         for (int y = playerFeet.getY() - searchDist; y <= playerFeet.getY() + searchDist; y++) {
            for (int z = playerFeet.getZ() - searchDist; z <= playerFeet.getZ() + searchDist; z++) {
               if (this.filter.has(bsi.get0(x, y, z))) {
                  BlockPos pos = new BlockPos(x, y, z);
                  if (this.baritone.settings().legitMineIncludeDiagonals.get() && this.knownOreLocations.stream().anyMatch(ore -> ore.distSqr(pos) <= 2.0)
                     || RotationUtils.reachable(this.ctx.entity(), pos, fakedBlockReachDistance).isPresent()) {
                     this.knownOreLocations.add(pos);
                  }
               }
            }
         }
      }

      this.knownOreLocations = prune(new CalculationContext(this.baritone), this.knownOreLocations, this.filter, 64, this.blacklist, dropped);
   }

   private static List<BlockPos> prune(
      CalculationContext ctx, List<BlockPos> locs2, BlockOptionalMetaLookup filter, int max, List<BlockPos> blacklist, List<BlockPos> dropped
   ) {
      dropped.removeIf(drop -> {
         for (BlockPos pos : locs2) {
            if (pos.distSqr(drop) <= 9.0 && filter.has(ctx.get(pos.getX(), pos.getY(), pos.getZ())) && plausibleToBreak(ctx, pos)) {
               return true;
            }
         }

         return false;
      });
      List<BlockPos> locs = locs2.stream()
         .distinct()
         .filter(
            pos -> !ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())
               || filter.has(ctx.get(pos.getX(), pos.getY(), pos.getZ()))
               || dropped.contains(pos)
         )
         .filter(pos -> plausibleToBreak(ctx, pos))
         .filter(pos -> ctx.getBaritone().settings().allowOnlyExposedOres.get() ? isNextToAir(ctx, pos) : true)
         .filter(pos -> pos.getY() >= ctx.getBaritone().settings().minYLevelWhileMining.get())
         .filter(pos -> !blacklist.contains(pos))
         .sorted(Comparator.comparingDouble(ctx.getBaritone().getEntityContext().entity().blockPosition()::distSqr))
         .collect(Collectors.toList());
      return locs.size() > max ? locs.subList(0, max) : locs;
   }

   public static boolean isNextToAir(CalculationContext ctx, BlockPos pos) {
      int radius = ctx.getBaritone().settings().allowOnlyExposedOresDistance.get();

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= radius
                  && MovementHelper.isTransparent(ctx.getBlock(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz))) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public static boolean plausibleToBreak(CalculationContext ctx, BlockPos pos) {
      return MovementHelper.getMiningDurationTicks(ctx, pos.getX(), pos.getY(), pos.getZ(), ctx.bsi.get0(pos), true) >= 1000000.0
         ? false
         : ctx.bsi.get0(pos.above()).getBlock() != Blocks.BEDROCK || ctx.bsi.get0(pos.below()).getBlock() != Blocks.BEDROCK;
   }

   @Override
   public void mineByName(int quantity, String... blocks) {
      this.mine(quantity, new BlockOptionalMetaLookup(this.baritone.getEntityContext().world(), blocks));
   }

   @Override
   public void mine(int quantity, BlockOptionalMetaLookup filter) {
      this.filter = filter;
      if (filter != null && !this.baritone.settings().allowBreak.get()) {
         this.logDirect("Unable to mine when allowBreak is false!");
         this.mine(quantity, (BlockOptionalMetaLookup)null);
      } else {
         this.desiredQuantity = quantity;
         this.knownOreLocations = new ArrayList<>();
         this.blacklist = new ArrayList<>();
         this.branchPoint = null;
         this.branchPointRunaway = null;
         this.anticipatedDrops = new HashMap<>();
         if (filter != null) {
            this.rescan(new ArrayList<>(), new CalculationContext(this.baritone));
         }
      }
   }

   @Override
   public void mine(int quantity, Block... blocks) {
      this.mine(
         quantity,
         new BlockOptionalMetaLookup(
            Stream.of(blocks).map(block -> new BlockOptionalMeta(this.baritone.getEntityContext().world(), block)).toArray(BlockOptionalMeta[]::new)
         )
      );
   }

   private static class GoalThreeBlocks extends GoalTwoBlocks {
      public GoalThreeBlocks(BlockPos pos) {
         super(pos);
      }

      @Override
      public boolean isInGoal(int x, int y, int z) {
         return x == this.x && (y == this.y || y == this.y - 1 || y == this.y - 2) && z == this.z;
      }

      @Override
      public double heuristic(int x, int y, int z) {
         int xDiff = x - this.x;
         int yDiff = y - this.y;
         int zDiff = z - this.z;
         return GoalBlock.calculate(xDiff, yDiff < -1 ? yDiff + 2 : (yDiff == -1 ? 0 : yDiff), zDiff);
      }
   }
}
