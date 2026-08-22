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

package com.player2.playerengine.automaton.pathing.path;

import com.player2.playerengine.automaton.api.pathing.calc.IPath;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.movement.IMovement;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import com.player2.playerengine.automaton.utils.pathing.PathBase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public class SplicedPath extends PathBase {
   private final List<BetterBlockPos> path;
   private final List<IMovement> movements;
   private final int numNodes;
   private final Goal goal;

   private SplicedPath(List<BetterBlockPos> path, List<IMovement> movements, int numNodesConsidered, Goal goal) {
      this.path = path;
      this.movements = movements;
      this.numNodes = numNodesConsidered;
      this.goal = goal;
      this.sanityCheck();
   }

   @Override
   public Goal getGoal() {
      return this.goal;
   }

   @Override
   public List<IMovement> movements() {
      return Collections.unmodifiableList(this.movements);
   }

   @Override
   public List<BetterBlockPos> positions() {
      return Collections.unmodifiableList(this.path);
   }

   @Override
   public int getNumNodesConsidered() {
      return this.numNodes;
   }

   @Override
   public int length() {
      return this.path.size();
   }

   public static Optional<SplicedPath> trySplice(IPath first, IPath second, boolean allowOverlapCutoff) {
      if (second != null && first != null) {
         if (!first.getDest().equals(second.getSrc())) {
            return Optional.empty();
         } else {
            HashSet<BetterBlockPos> secondPos = new HashSet<>(second.positions());
            int firstPositionInSecond = -1;

            for (int i = 0; i < first.length() - 1; i++) {
               if (secondPos.contains(first.positions().get(i))) {
                  firstPositionInSecond = i;
                  break;
               }
            }

            if (firstPositionInSecond != -1) {
               if (!allowOverlapCutoff) {
                  return Optional.empty();
               }
            } else {
               firstPositionInSecond = first.length() - 1;
            }

            int positionInSecond = second.positions().indexOf(first.positions().get(firstPositionInSecond));
            if (!allowOverlapCutoff && positionInSecond != 0) {
               throw new IllegalStateException();
            } else {
               List<BetterBlockPos> positions = new ArrayList<>();
               List<IMovement> movements = new ArrayList<>();
               positions.addAll(first.positions().subList(0, firstPositionInSecond + 1));
               movements.addAll(first.movements().subList(0, firstPositionInSecond));
               positions.addAll(second.positions().subList(positionInSecond + 1, second.length()));
               movements.addAll(second.movements().subList(positionInSecond, second.length() - 1));
               return Optional.of(new SplicedPath(positions, movements, first.getNumNodesConsidered() + second.getNumNodesConsidered(), first.getGoal()));
            }
         }
      } else {
         return Optional.empty();
      }
   }
}
