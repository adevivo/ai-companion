package com.player2.playerengine.trackers;

import com.player2.playerengine.PlayerEngineController;
import java.util.ArrayList;

public class TrackerManager {
   private final ArrayList<Tracker> trackers = new ArrayList<>();
   private final PlayerEngineController mod;
   private boolean wasInGame = false;

   public TrackerManager(PlayerEngineController mod) {
      this.mod = mod;
   }

   public void tick() {
      boolean inGame = PlayerEngineController.inGame();
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

   public PlayerEngineController getController() {
      return this.mod;
   }
}
