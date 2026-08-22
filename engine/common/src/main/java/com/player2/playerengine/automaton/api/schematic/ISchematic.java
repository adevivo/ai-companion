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

package com.player2.playerengine.automaton.api.schematic;

import java.util.List;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;

public interface ISchematic {
   default boolean inSchematic(int x, int y, int z, BlockState currentState) {
      return x >= 0 && x < this.widthX() && y >= 0 && y < this.heightY() && z >= 0 && z < this.lengthZ();
   }

   default int size(Axis axis) {
      switch (axis) {
         case X:
            return this.widthX();
         case Y:
            return this.heightY();
         case Z:
            return this.lengthZ();
         default:
            throw new UnsupportedOperationException(axis + "");
      }
   }

   BlockState desiredState(int var1, int var2, int var3, BlockState var4, List<BlockState> var5);

   default void reset() {
   }

   int widthX();

   int heightY();

   int lengthZ();
}
