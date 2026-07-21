package baritone.cache;

import baritone.api.cache.IWaypoint;
import baritone.api.cache.IWaypointCollection;
import baritone.api.cache.Waypoint;
import baritone.api.utils.BetterBlockPos;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;

public class WaypointCollection implements IWaypointCollection {
   private final Map<IWaypoint.Tag, Set<IWaypoint>> waypoints = new EnumMap<>(
      Arrays.stream(IWaypoint.Tag.values()).collect(Collectors.toMap(Function.identity(), t -> new HashSet<>()))
   );

   WaypointCollection() {
   }

   public void readFromNbt(CompoundTag nbt) {
      for (IWaypoint.Tag tag : IWaypoint.Tag.values()) {
         this.waypoints.put(tag, this.readFromNbt(tag, nbt.getList(tag.name(), 10)));
      }
   }

   private synchronized Set<IWaypoint> readFromNbt(IWaypoint.Tag tag, ListTag nbt) {
      Set<IWaypoint> ret = new HashSet<>();

      for (int i = 0; i < nbt.size(); i++) {
         CompoundTag in = nbt.getCompound(i);
         String name = in.getString("name");
         long creationTimestamp = in.getLong("created");
         BetterBlockPos pos = new BetterBlockPos(NbtUtils.readBlockPos(in.getCompound("pos")));
         ret.add(new Waypoint(name, tag, pos, creationTimestamp));
      }

      return ret;
   }

   public CompoundTag toNbt() {
      CompoundTag nbt = new CompoundTag();

      for (IWaypoint.Tag waypointTag : IWaypoint.Tag.values()) {
         nbt.put(waypointTag.name(), this.save(waypointTag));
      }

      return nbt;
   }

   private synchronized ListTag save(IWaypoint.Tag waypointTag) {
      ListTag list = new ListTag();

      for (IWaypoint waypoint : this.waypoints.get(waypointTag)) {
         CompoundTag serializedWaypoint = new CompoundTag();
         serializedWaypoint.putString("name", waypoint.getName());
         serializedWaypoint.putLong("created", waypoint.getCreationTimestamp());
         serializedWaypoint.put("pos", NbtUtils.writeBlockPos(waypoint.getLocation()));
         list.add(serializedWaypoint);
      }

      return list;
   }

   @Override
   public void addWaypoint(IWaypoint waypoint) {
      this.waypoints.get(waypoint.getTag()).add(waypoint);
   }

   @Override
   public void removeWaypoint(IWaypoint waypoint) {
      this.waypoints.get(waypoint.getTag()).remove(waypoint);
   }

   @Override
   public IWaypoint getMostRecentByTag(IWaypoint.Tag tag) {
      return this.waypoints.get(tag).stream().min(Comparator.comparingLong(w -> -w.getCreationTimestamp())).orElse(null);
   }

   @Override
   public Set<IWaypoint> getByTag(IWaypoint.Tag tag) {
      return Collections.unmodifiableSet(this.waypoints.get(tag));
   }

   @Override
   public Set<IWaypoint> getAllWaypoints() {
      return this.waypoints.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
   }
}
