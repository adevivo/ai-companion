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

import com.player2.playerengine.automaton.api.utils.BlockOptionalMetaLookup;
import net.minecraft.world.level.block.state.BlockState;

public class ReplaceSchematic extends MaskSchematic {
   private final BlockOptionalMetaLookup filter;
   private final Boolean[][][] cache;

   public ReplaceSchematic(ISchematic schematic, BlockOptionalMetaLookup filter) {
      super(schematic);
      this.filter = filter;
      this.cache = new Boolean[this.widthX()][this.heightY()][this.lengthZ()];
   }

   @Override
   public void reset() {
      for (int x = 0; x < this.cache.length; x++) {
         for (int y = 0; y < this.cache[0].length; y++) {
            for (int z = 0; z < this.cache[0][0].length; z++) {
               this.cache[x][y][z] = null;
            }
         }
      }
   }

   @Override
   protected boolean partOfMask(int x, int y, int z, BlockState currentState) {
      if (this.cache[x][y][z] == null) {
         this.cache[x][y][z] = this.filter.has(currentState);
      }

      return this.cache[x][y][z];
   }
}
