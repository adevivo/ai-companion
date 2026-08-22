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
import net.minecraft.world.level.block.state.BlockState;

public abstract class MaskSchematic extends AbstractSchematic {
   private final ISchematic schematic;

   public MaskSchematic(ISchematic schematic) {
      super(schematic.widthX(), schematic.heightY(), schematic.lengthZ());
      this.schematic = schematic;
   }

   protected abstract boolean partOfMask(int var1, int var2, int var3, BlockState var4);

   @Override
   public boolean inSchematic(int x, int y, int z, BlockState currentState) {
      return this.schematic.inSchematic(x, y, z, currentState) && this.partOfMask(x, y, z, currentState);
   }

   @Override
   public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
      return this.schematic.desiredState(x, y, z, current, approxPlaceable);
   }
}
