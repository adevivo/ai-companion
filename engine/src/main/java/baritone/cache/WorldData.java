package baritone.cache;

import baritone.api.cache.ICachedWorld;
import baritone.api.cache.IContainerMemory;
import baritone.api.cache.IWaypointCollection;
import baritone.api.cache.IWorldData;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class WorldData implements IWorldData {
   private final WaypointCollection waypoints = new WaypointCollection();
   private final ContainerMemory containerMemory = new ContainerMemory();
   public final ResourceKey<Level> dimension;

   WorldData(ResourceKey<Level> dimension) {
      this.dimension = dimension;
   }

   public void readFromNbt(CompoundTag tag) {
      this.containerMemory.read(tag.getCompound("containers"));
      this.waypoints.readFromNbt(tag.getCompound("waypoints"));
   }

   public void writeToNbt(CompoundTag tag) {
      tag.put("containers", this.containerMemory.toNbt());
      tag.put("waypoints", this.waypoints.toNbt());
   }

   @Override
   public ICachedWorld getCachedWorld() {
      return new ICachedWorld() {
         @Override
         public boolean isCached(int blockX, int blockZ) {
            return false;
         }

         @Override
         public ArrayList<BlockPos> getLocationsOf(String block, int maximum, int centerX, int centerZ, int maxRegionDistanceSq) {
            return new ArrayList<>();
         }
      };
   }

   @Override
   public IWaypointCollection getWaypoints() {
      return this.waypoints;
   }

   @Override
   public IContainerMemory getContainerMemory() {
      return this.containerMemory;
   }
}
