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
import com.player2.playerengine.automaton.api.utils.interfaces.IGoalRenderPos;
import net.minecraft.core.BlockPos;

public class GoalTwoBlocks implements Goal, IGoalRenderPos {
   protected final int x;
   protected final int y;
   protected final int z;

   public GoalTwoBlocks(BlockPos pos) {
      this(pos.getX(), pos.getY(), pos.getZ());
   }

   public GoalTwoBlocks(int x, int y, int z) {
      this.x = x;
      this.y = y;
      this.z = z;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      return x == this.x && (y == this.y || y == this.y - 1) && z == this.z;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      int xDiff = x - this.x;
      int yDiff = y - this.y;
      int zDiff = z - this.z;
      return GoalBlock.calculate(xDiff, yDiff < 0 ? yDiff + 1 : yDiff, zDiff);
   }

   @Override
   public BlockPos getGoalPos() {
      return new BlockPos(this.x, this.y, this.z);
   }

   @Override
   public String toString() {
      return String.format(
         "GoalTwoBlocks{x=%s,y=%s,z=%s}", SettingsUtil.maybeCensor(this.x), SettingsUtil.maybeCensor(this.y), SettingsUtil.maybeCensor(this.z)
      );
   }
}
