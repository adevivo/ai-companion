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

package com.player2.playerengine.automaton.api.pathing.goals;

import com.player2.playerengine.automaton.api.pathing.movement.ActionCosts;
import com.player2.playerengine.automaton.api.utils.SettingsUtil;

public class GoalYLevel implements Goal, ActionCosts {
   public final int level;

   public GoalYLevel(int level) {
      this.level = level;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      return y == this.level;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      return calculate(this.level, y);
   }

   public static double calculate(int goalY, int currentY) {
      if (currentY > goalY) {
         return FALL_N_BLOCKS_COST[2] / 2.0 * (currentY - goalY);
      } else {
         return currentY < goalY ? (goalY - currentY) * JUMP_ONE_BLOCK_COST : 0.0;
      }
   }

   @Override
   public String toString() {
      return String.format("GoalYLevel{y=%s}", SettingsUtil.maybeCensor(this.level));
   }
}
