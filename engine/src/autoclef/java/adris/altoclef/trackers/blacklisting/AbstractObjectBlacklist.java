package adris.altoclef.trackers.blacklisting;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.helpers.StorageHelper;
import java.util.HashMap;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractObjectBlacklist<T> {
   private final HashMap<T, AbstractObjectBlacklist.BlacklistEntry> entries = new HashMap<>();

   public void blackListItem(AltoClefController mod, T item, int numberOfFailuresAllowed) {
      if (!this.entries.containsKey(item)) {
         AbstractObjectBlacklist.BlacklistEntry blacklistEntry = new AbstractObjectBlacklist.BlacklistEntry();
         blacklistEntry.numberOfFailuresAllowed = numberOfFailuresAllowed;
         blacklistEntry.numberOfFailures = 0;
         blacklistEntry.bestDistanceSq = Double.POSITIVE_INFINITY;
         blacklistEntry.bestTool = MiningRequirement.HAND;
         this.entries.put(item, blacklistEntry);
      }

      AbstractObjectBlacklist.BlacklistEntry entry = this.entries.get(item);
      double newDistance = this.getPos(item).distanceToSqr(mod.getPlayer().position());
      MiningRequirement newTool = StorageHelper.getCurrentMiningRequirement(mod);
      if (newTool.ordinal() > entry.bestTool.ordinal() || newDistance < entry.bestDistanceSq - 1.0) {
         if (newTool.ordinal() > entry.bestTool.ordinal()) {
            entry.bestTool = newTool;
         }

         if (newDistance < entry.bestDistanceSq) {
            entry.bestDistanceSq = newDistance;
         }

         entry.numberOfFailures = 0;
         Debug.logMessage("Blacklist RESET: " + item.toString());
      }

      entry.numberOfFailures++;
      entry.numberOfFailuresAllowed = numberOfFailuresAllowed;
      Debug.logMessage("Blacklist: " + item.toString() + ": Try " + entry.numberOfFailures + " / " + entry.numberOfFailuresAllowed);
   }

   protected abstract Vec3 getPos(T var1);

   public boolean unreachable(T item) {
      if (this.entries.containsKey(item)) {
         AbstractObjectBlacklist.BlacklistEntry entry = this.entries.get(item);
         return entry.numberOfFailures > entry.numberOfFailuresAllowed;
      } else {
         return false;
      }
   }

   public void clear() {
      this.entries.clear();
   }

   private static class BlacklistEntry {
      public int numberOfFailuresAllowed;
      public int numberOfFailures;
      public double bestDistanceSq;
      public MiningRequirement bestTool;
   }
}
