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

import com.player2.playerengine.automaton.api.utils.SettingsUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class GoalStrictDirection implements Goal {
   public final int x;
   public final int y;
   public final int z;
   public final int dx;
   public final int dz;

   public GoalStrictDirection(BlockPos origin, Direction direction) {
      this.x = origin.getX();
      this.y = origin.getY();
      this.z = origin.getZ();
      this.dx = direction.getStepX();
      this.dz = direction.getStepZ();
      if (this.dx == 0 && this.dz == 0) {
         throw new IllegalArgumentException(direction + "");
      }
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      return false;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      int distanceFromStartInDesiredDirection = (x - this.x) * this.dx + (z - this.z) * this.dz;
      int distanceFromStartInIncorrectDirection = Math.abs((x - this.x) * this.dz) + Math.abs((z - this.z) * this.dx);
      int verticalDistanceFromStart = Math.abs(y - this.y);
      double heuristic = -distanceFromStartInDesiredDirection * 100;
      heuristic += distanceFromStartInIncorrectDirection * 1000;
      return heuristic + verticalDistanceFromStart * 1000;
   }

   @Override
   public double heuristic() {
      return Double.NEGATIVE_INFINITY;
   }

   @Override
   public String toString() {
      return String.format(
         "GoalStrictDirection{x=%s, y=%s, z=%s, dx=%s, dz=%s}",
         SettingsUtil.maybeCensor(this.x),
         SettingsUtil.maybeCensor(this.y),
         SettingsUtil.maybeCensor(this.z),
         SettingsUtil.maybeCensor(this.dx),
         SettingsUtil.maybeCensor(this.dz)
      );
   }
}
