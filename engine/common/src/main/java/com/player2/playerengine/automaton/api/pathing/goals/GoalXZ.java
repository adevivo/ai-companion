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

import com.player2.playerengine.automaton.api.BaritoneAPI;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import com.player2.playerengine.automaton.api.utils.SettingsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class GoalXZ implements Goal {
   private static final double SQRT_2 = Math.sqrt(2.0);
   private final int x;
   private final int z;

   public GoalXZ(int x, int z) {
      this.x = x;
      this.z = z;
   }

   public GoalXZ(BetterBlockPos pos) {
      this.x = pos.x;
      this.z = pos.z;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      return x == this.x && z == this.z;
   }

   @Override
   public double heuristic(int x, int y, int z) {
      int xDiff = x - this.x;
      int zDiff = z - this.z;
      return calculate(xDiff, zDiff);
   }

   @Override
   public String toString() {
      return String.format("GoalXZ{x=%s,z=%s}", SettingsUtil.maybeCensor(this.x), SettingsUtil.maybeCensor(this.z));
   }

   public static double calculate(double xDiff, double zDiff) {
      double x = Math.abs(xDiff);
      double z = Math.abs(zDiff);
      double straight;
      double diagonal;
      if (x < z) {
         straight = z - x;
         diagonal = x;
      } else {
         straight = x - z;
         diagonal = z;
      }

      diagonal *= SQRT_2;
      return (diagonal + straight) * BaritoneAPI.getGlobalSettings().costHeuristic.get();
   }

   public static GoalXZ fromDirection(Vec3 origin, float yaw, double distance) {
      float theta = (float)Math.toRadians(yaw);
      double x = origin.x - Mth.sin(theta) * distance;
      double z = origin.z + Mth.cos(theta) * distance;
      return new GoalXZ(Mth.floor(x), Mth.floor(z));
   }

   public int getX() {
      return this.x;
   }

   public int getZ() {
      return this.z;
   }
}
