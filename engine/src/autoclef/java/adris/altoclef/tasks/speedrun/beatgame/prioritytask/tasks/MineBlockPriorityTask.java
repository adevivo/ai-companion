package adris.altoclef.tasks.speedrun.beatgame.prioritytask.tasks;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.resources.MineAndCollectTask;
import adris.altoclef.tasks.speedrun.beatgame.prioritytask.prioritycalculators.DistancePriorityCalculator;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.helpers.StorageHelper;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public class MineBlockPriorityTask extends PriorityTask {
   public final Block[] toMine;
   public final Item[] droppedItem;
   public final ItemTarget[] droppedItemTargets;
   private final MiningRequirement miningRequirement;
   private final DistancePriorityCalculator prioritySupplier;

   public MineBlockPriorityTask(Block[] toMine, Item[] droppedItem, MiningRequirement miningRequirement, DistancePriorityCalculator prioritySupplier) {
      this(toMine, droppedItem, miningRequirement, prioritySupplier, false, true, false);
   }

   public MineBlockPriorityTask(
      Block[] toMine,
      Item[] droppedItem,
      MiningRequirement miningRequirement,
      DistancePriorityCalculator prioritySupplier,
      Function<AltoClefController, Boolean> canCall
   ) {
      this(toMine, droppedItem, miningRequirement, prioritySupplier, canCall, false, true, false);
   }

   public MineBlockPriorityTask(
      Block[] toMine,
      Item[] droppedItem,
      MiningRequirement miningRequirement,
      DistancePriorityCalculator prioritySupplier,
      boolean shouldForce,
      boolean canCache,
      boolean bypassForceCooldown
   ) {
      this(toMine, droppedItem, miningRequirement, prioritySupplier, mod -> true, shouldForce, canCache, bypassForceCooldown);
   }

   public MineBlockPriorityTask(
      Block[] toMine,
      Item[] droppedItem,
      MiningRequirement miningRequirement,
      DistancePriorityCalculator prioritySupplier,
      Function<AltoClefController, Boolean> canCall,
      boolean shouldForce,
      boolean canCache,
      boolean bypassForceCooldown
   ) {
      super(canCall, shouldForce, canCache, bypassForceCooldown);
      this.toMine = toMine;
      this.droppedItem = droppedItem;
      this.droppedItemTargets = ItemTarget.of(droppedItem);
      this.miningRequirement = miningRequirement;
      this.prioritySupplier = prioritySupplier;
   }

   @Override
   public Task getTask(AltoClefController mod) {
      return new MineAndCollectTask(this.droppedItemTargets, this.toMine, this.miningRequirement);
   }

   @Override
   public String getDebugString() {
      return "Gathering resource: " + Arrays.toString((Object[])this.droppedItem);
   }

   @Override
   protected double getPriority(AltoClefController mod) {
      if (!StorageHelper.miningRequirementMet(mod, this.miningRequirement)) {
         return Double.NEGATIVE_INFINITY;
      } else {
         double closestDist = this.getClosestDist(mod);
         int itemCount = mod.getItemStorage().getItemCount(this.droppedItem);
         this.prioritySupplier.update(itemCount);
         return this.prioritySupplier.getPriority(closestDist);
      }
   }

   private double getClosestDist(AltoClefController mod) {
      Vec3 pos = mod.getPlayer().position();
      Tuple<Double, Optional<BlockPos>> closestBlock = MineAndCollectTask.MineOrCollectTask.getClosestBlock(mod, pos, this.toMine);
      Tuple<Double, Optional<ItemEntity>> closestDrop = MineAndCollectTask.MineOrCollectTask.getClosestItemDrop(mod, pos, this.droppedItemTargets);
      return Math.min((Double)closestBlock.getA(), (Double)closestDrop.getA());
   }
}
