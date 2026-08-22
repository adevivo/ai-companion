package com.player2.playerengine.trackers;

import com.player2.playerengine.PlayerEngineController;

public abstract class Tracker {
   protected PlayerEngineController mod;
   private boolean dirty = true;

   public Tracker(TrackerManager manager) {
      manager.addTracker(this);
   }

   public void setDirty() {
      this.dirty = true;
   }

   protected boolean isDirty() {
      return this.dirty;
   }

   public void ensureUpdated() {
      if (this.isDirty()) {
         this.updateState();
         this.dirty = false;
      }
   }

   protected abstract void updateState();

   protected abstract void reset();
}
