package adris.altoclef.trackers;

import adris.altoclef.AltoClefController;
import java.util.ArrayList;

public class TrackerManager {
   private final ArrayList<Tracker> trackers = new ArrayList<>();
   private final AltoClefController mod;
   private boolean wasInGame = false;

   public TrackerManager(AltoClefController mod) {
      this.mod = mod;
   }

   public void tick() {
      boolean inGame = AltoClefController.inGame();
      if (!inGame && this.wasInGame) {
         for (Tracker tracker : this.trackers) {
            tracker.reset();
         }

         this.mod.getChunkTracker().reset(this.mod);
         this.mod.getMiscBlockTracker().reset();
      }

      this.wasInGame = inGame;

      for (Tracker tracker : this.trackers) {
         tracker.setDirty();
      }
   }

   public void addTracker(Tracker tracker) {
      tracker.mod = this.mod;
      this.trackers.add(tracker);
   }

   public AltoClefController getController() {
      return this.mod;
   }
}
