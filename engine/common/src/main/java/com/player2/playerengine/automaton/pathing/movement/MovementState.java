/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.player2.playerengine.automaton.pathing.movement;

import com.player2.playerengine.automaton.api.pathing.movement.MovementStatus;
import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.input.Input;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MovementState {
   private MovementStatus status;
   private MovementState.MovementTarget target = new MovementState.MovementTarget();
   private final Map<Input, Boolean> inputState = new HashMap<>();

   public MovementState setStatus(MovementStatus status) {
      this.status = status;
      return this;
   }

   public MovementStatus getStatus() {
      return this.status;
   }

   public MovementState.MovementTarget getTarget() {
      return this.target;
   }

   public MovementState setTarget(MovementState.MovementTarget target) {
      this.target = target;
      return this;
   }

   public MovementState setInput(Input input, boolean forced) {
      this.inputState.put(input, forced);
      return this;
   }

   public Map<Input, Boolean> getInputStates() {
      return this.inputState;
   }

   public static class MovementTarget {
      public Rotation rotation;
      private boolean forceRotations;

      public MovementTarget() {
         this(null, false);
      }

      public MovementTarget(Rotation rotation, boolean forceRotations) {
         this.rotation = rotation;
         this.forceRotations = forceRotations;
      }

      public final Optional<Rotation> getRotation() {
         return Optional.ofNullable(this.rotation);
      }

      public boolean hasToForceRotations() {
         return this.forceRotations;
      }
   }
}
