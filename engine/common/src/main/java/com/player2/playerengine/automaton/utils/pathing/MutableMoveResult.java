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

package com.player2.playerengine.automaton.utils.pathing;


public final class MutableMoveResult {
   public int x;
   public int y;
   public int z;
   public double cost;
   public double oxygenCost;

   public MutableMoveResult() {
      this.reset();
   }

   public final void reset() {
      this.x = 0;
      this.y = 0;
      this.z = 0;
      this.cost = 1000000.0;
      this.oxygenCost = 0.0;
   }
}
