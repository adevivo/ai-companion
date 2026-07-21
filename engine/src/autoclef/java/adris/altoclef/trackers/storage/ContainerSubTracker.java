package adris.altoclef.trackers.storage;

import adris.altoclef.AltoClefController;
import adris.altoclef.trackers.Tracker;
import adris.altoclef.trackers.TrackerManager;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ContainerSubTracker extends Tracker {
   private final HashMap<Dimension, HashMap<BlockPos, ContainerCache>> containerCaches = new HashMap<>();
   private BlockPos lastInteractedContainer;

   public ContainerSubTracker(TrackerManager manager) {
      super(manager);

      for (Dimension dimension : Dimension.values()) {
         this.containerCaches.put(dimension, new HashMap<>());
      }
   }

   public Optional<ContainerCache> WritableCache(AltoClefController controller, BlockPos pos) {
      if (controller.getWorld().getBlockEntity(pos) instanceof Container containerInventory) {
         this.lastInteractedContainer = pos;
         Block block = controller.getWorld().getBlockState(pos).getBlock();
         ContainerType type = ContainerType.getFromBlock(block);
         if (type == ContainerType.EMPTY) {
            return Optional.empty();
         } else {
            ContainerCache cache = this.containerCaches
               .get(WorldHelper.getCurrentDimension(controller))
               .computeIfAbsent(pos, p -> new ContainerCache(WorldHelper.getCurrentDimension(controller), p, type));
            cache.update(containerInventory, s -> {});
            return Optional.of(cache);
         }
      } else {
         this.containerCaches.get(WorldHelper.getCurrentDimension(controller)).remove(pos);
         return Optional.empty();
      }
   }

   public Optional<ContainerCache> getContainerAtPosition(BlockPos pos) {
      return Optional.ofNullable(this.containerCaches.get(WorldHelper.getCurrentDimension(this.mod)).get(pos));
   }

   public List<ContainerCache> getCachedContainers(Predicate<ContainerCache> accept) {
      List<ContainerCache> result = new ArrayList<>();
      this.containerCaches.get(WorldHelper.getCurrentDimension(this.mod)).values().forEach(cache -> {
         if (accept.test(cache)) {
            result.add(cache);
         }
      });
      return result;
   }

   public List<ContainerCache> getContainersWithItem(Item... items) {
      return this.getCachedContainers(cache -> cache.hasItem(items));
   }

   public Optional<BlockPos> getLastInteractedContainer() {
      return Optional.ofNullable(this.lastInteractedContainer);
   }

   @Override
   protected void updateState() {
   }

   @Override
   protected void reset() {
      this.containerCaches.values().forEach(HashMap::clear);
      this.lastInteractedContainer = null;
   }
}
