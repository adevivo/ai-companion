package com.player2.playerengine.control;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.input.Input;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public class InputControls {
   PlayerEngineController controller;
   private final Queue<Input> toUnpress = new ArrayDeque<>();
   private final Set<Input> waitForRelease = new HashSet<>();

   public InputControls(PlayerEngineController controller) {
      this.controller = controller;
   }

   public void tryPress(Input input) {
      if (!this.waitForRelease.contains(input)) {
         this.controller.getBaritone().getInputOverrideHandler().setInputForceState(input, true);
         this.toUnpress.add(input);
         this.waitForRelease.add(input);
      }
   }

   public void hold(Input input) {
      this.controller.getBaritone().getInputOverrideHandler().setInputForceState(input, true);
   }

   public void release(Input input) {
      this.controller.getBaritone().getInputOverrideHandler().setInputForceState(input, false);
   }

   public boolean isHeldDown(Input input) {
      return this.controller.getBaritone().getInputOverrideHandler().isInputForcedDown(input);
   }

   public void forceLook(float yaw, float pitch) {
      this.controller.getBaritone().getLookBehavior().updateTarget(new Rotation(yaw, pitch), true);
   }

   public void onTickPre() {
      while (!this.toUnpress.isEmpty()) {
         this.controller.getBaritone().getInputOverrideHandler().setInputForceState(this.toUnpress.remove(), false);
      }
   }

   public void onTickPost() {
      this.waitForRelease.clear();
   }
}
