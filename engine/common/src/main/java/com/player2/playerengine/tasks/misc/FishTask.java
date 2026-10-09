package com.player2.playerengine.tasks.misc;

import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.process.FishingProcess;
import net.minecraft.world.item.Items;

public class FishTask extends Task {
   /**
    * Ticks to wait before restarting a fishing process that stopped itself. The process gives up on
    * its own when it finds no water within 8 blocks or cannot path to it, and restarting it on the
    * very next tick looped: "Fishing process started." about 20 times a second in chat, 793 lines
    * in 39 s on 2026-10-09, until the owner moved the companion.
    */
   private static final int RETRY_DELAY_TICKS = 100;
   private int retryCooldown = 0;

   @Override
   protected void onStart() {
      this.retryCooldown = 0;
      ((Baritone)this.controller.getBaritone()).getFishingProcess().fish();
   }

   @Override
   protected Task onTick() {
      FishingProcess fishingProcess = ((Baritone)this.controller.getBaritone()).getFishingProcess();
      if (!this.controller.getSlotHandler().forceEquipItem(Items.FISHING_ROD)) {
         this.setDebugState("Can't fish without a fishing rod");
         return null;
      } else {
         if (!fishingProcess.isActive()) {
            if (this.retryCooldown <= 0) {
               this.retryCooldown = RETRY_DELAY_TICKS;
               fishingProcess.fish();
            } else {
               this.retryCooldown--;
               this.setDebugState("No reachable water to fish from nearby; trying again shortly");
               return null;
            }
         }

         this.setDebugState("Fishing with Automatone...");
         return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      FishingProcess fishingProcess = ((Baritone)this.controller.getBaritone()).getFishingProcess();
      if (fishingProcess.isActive()) {
         fishingProcess.onLostControl();
      }
   }

   @Override
   public boolean isFinished() {
      return false;
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof FishTask;
   }

   @Override
   protected String toDebugString() {
      return "Fishing";
   }
}
