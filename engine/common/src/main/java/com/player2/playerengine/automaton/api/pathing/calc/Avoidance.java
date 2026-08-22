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

package com.player2.playerengine.automaton.api.pathing.calc;

import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;

public class Avoidance {
   private final int centerX;
   private final int centerY;
   private final int centerZ;
   private final double coefficient;
   private final int radius;
   private final int radiusSq;

   public Avoidance(BlockPos center, double coefficient, int radius) {
      this(center.getX(), center.getY(), center.getZ(), coefficient, radius);
   }

   public Avoidance(int centerX, int centerY, int centerZ, double coefficient, int radius) {
      this.centerX = centerX;
      this.centerY = centerY;
      this.centerZ = centerZ;
      this.coefficient = coefficient;
      this.radius = radius;
      this.radiusSq = radius * radius;
   }

   public double coefficient(int x, int y, int z) {
      int xDiff = x - this.centerX;
      int yDiff = y - this.centerY;
      int zDiff = z - this.centerZ;
      return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff <= this.radiusSq ? this.coefficient : 1.0;
   }

   public void applySpherical(Long2DoubleOpenHashMap map) {
      for (int x = -this.radius; x <= this.radius; x++) {
         for (int y = -this.radius; y <= this.radius; y++) {
            for (int z = -this.radius; z <= this.radius; z++) {
               if (x * x + y * y + z * z <= this.radius * this.radius) {
                  long hash = BetterBlockPos.longHash(this.centerX + x, this.centerY + y, this.centerZ + z);
                  map.put(hash, map.get(hash) * this.coefficient);
               }
            }
         }
      }
   }
}
