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

package com.player2.playerengine.automaton.pathing.calc;

import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;

public final class PathNode {
   public final int x;
   public final int y;
   public final int z;
   public final double estimatedCostToGoal;
   public double cost;
   public double oxygenCost;
   public double combinedCost;
   public PathNode previous = null;
   public int heapPosition;

   public PathNode(int x, int y, int z, Goal goal) {
      this.cost = 1000000.0;
      this.oxygenCost = 0.0;
      this.estimatedCostToGoal = goal.heuristic(x, y, z);
      if (Double.isNaN(this.estimatedCostToGoal)) {
         throw new IllegalStateException(goal + " calculated implausible heuristic");
      } else {
         this.heapPosition = -1;
         this.x = x;
         this.y = y;
         this.z = z;
      }
   }

   public boolean isOpen() {
      return this.heapPosition != -1;
   }

   @Override
   public int hashCode() {
      return (int)BetterBlockPos.longHash(this.x, this.y, this.z);
   }

   @Override
   public boolean equals(Object obj) {
      PathNode other = (PathNode)obj;
      return this.x == other.x && this.y == other.y && this.z == other.z;
   }
}
