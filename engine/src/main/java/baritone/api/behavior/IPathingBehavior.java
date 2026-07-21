package baritone.api.behavior;

import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.calc.IPathFinder;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.utils.BetterBlockPos;
import java.util.Optional;
import java.util.OptionalDouble;

public interface IPathingBehavior extends IBehavior {
   default OptionalDouble ticksRemainingInSegment() {
      return this.ticksRemainingInSegment(true);
   }

   default OptionalDouble ticksRemainingInSegment(boolean includeCurrentMovement) {
      IPathExecutor current = this.getCurrent();
      if (current == null) {
         return OptionalDouble.empty();
      } else {
         int start = includeCurrentMovement ? current.getPosition() : current.getPosition() + 1;
         return OptionalDouble.of(current.getPath().ticksRemainingFrom(start));
      }
   }

   Optional<Double> estimatedTicksToGoal();

   Goal getGoal();

   boolean isPathing();

   default boolean hasPath() {
      return this.getCurrent() != null;
   }

   boolean cancelEverything();

   void forceCancel();

   default Optional<IPath> getPath() {
      return Optional.ofNullable(this.getCurrent()).map(IPathExecutor::getPath);
   }

   Optional<? extends IPathFinder> getInProgress();

   IPathExecutor getCurrent();

   IPathExecutor getNext();

   BetterBlockPos pathStart();

   boolean isSafeToCancel();
}
