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

package com.player2.playerengine.automaton.api.behavior;

import com.player2.playerengine.automaton.api.pathing.calc.IPath;
import com.player2.playerengine.automaton.api.pathing.calc.IPathFinder;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.path.IPathExecutor;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
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
