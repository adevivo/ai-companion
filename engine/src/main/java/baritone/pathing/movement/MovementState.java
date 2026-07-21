package baritone.pathing.movement;

import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
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
