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

package com.player2.playerengine.automaton.api.pathing.movement;

public interface ActionCosts {
   double WALK_ONE_BLOCK_COST = 4.63284688441047;
   double WALK_ONE_IN_WATER_COST = 9.09090909090909;
   double LADDER_UP_ONE_COST = 8.51063829787234;
   double LADDER_DOWN_ONE_COST = 6.666666666666667;
   double SNEAK_ONE_BLOCK_COST = 15.384615384615383;
   double SPRINT_ONE_BLOCK_COST = 3.563791874554526;
   double SPRINT_MULTIPLIER = 0.7692444761225944;
   double WALK_OFF_BLOCK_COST = 3.7062775075283763;
   double CENTER_AFTER_FALL_COST = 0.9265693768820937;
   double COST_INF = 1000000.0;
   double[] FALL_N_BLOCKS_COST = generateFallNBlocksCost();
   double FALL_1_25_BLOCKS_COST = distanceToTicks(1.25);
   double FALL_0_25_BLOCKS_COST = distanceToTicks(0.25);
   double JUMP_ONE_BLOCK_COST = FALL_1_25_BLOCKS_COST - FALL_0_25_BLOCKS_COST;

   static double[] generateFallNBlocksCost() {
      double[] costs = new double[513];

      for (int i = 0; i < 257; i++) {
         costs[i] = distanceToTicks(i);
      }

      return costs;
   }

   static double velocity(int ticks) {
      return (Math.pow(0.98, ticks) - 1.0) * -3.92;
   }

   static double oldFormula(double ticks) {
      return -3.92 * (99.0 - 49.5 * (Math.pow(0.98, ticks) + 1.0) - ticks);
   }

   static double distanceToTicks(double distance) {
      if (distance == 0.0) {
         return 0.0;
      } else {
         double tmpDistance = distance;
         int tickCount = 0;

         while (true) {
            double fallDistance = velocity(tickCount);
            if (tmpDistance <= fallDistance) {
               return tickCount + tmpDistance / fallDistance;
            }

            tmpDistance -= fallDistance;
            tickCount++;
         }
      }
   }
}
