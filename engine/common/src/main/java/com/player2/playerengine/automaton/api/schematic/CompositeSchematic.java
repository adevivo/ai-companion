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

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

public class CompositeSchematic extends AbstractSchematic {
   private final List<CompositeSchematicEntry> schematics = new ArrayList<>();
   private CompositeSchematicEntry[] schematicArr;

   private void recalcArr() {
      this.schematicArr = this.schematics.toArray(new CompositeSchematicEntry[0]);

      for (CompositeSchematicEntry entry : this.schematicArr) {
         this.x = Math.max(this.x, entry.x + entry.schematic.widthX());
         this.y = Math.max(this.y, entry.y + entry.schematic.heightY());
         this.z = Math.max(this.z, entry.z + entry.schematic.lengthZ());
      }
   }

   public CompositeSchematic(int x, int y, int z) {
      super(x, y, z);
      this.recalcArr();
   }

   public void put(ISchematic extra, int x, int y, int z) {
      this.schematics.add(new CompositeSchematicEntry(extra, x, y, z));
      this.recalcArr();
   }

   private CompositeSchematicEntry getSchematic(int x, int y, int z, BlockState currentState) {
      for (CompositeSchematicEntry entry : this.schematicArr) {
         if (x >= entry.x && y >= entry.y && z >= entry.z && entry.schematic.inSchematic(x - entry.x, y - entry.y, z - entry.z, currentState)) {
            return entry;
         }
      }

      return null;
   }

   @Override
   public boolean inSchematic(int x, int y, int z, BlockState currentState) {
      CompositeSchematicEntry entry = this.getSchematic(x, y, z, currentState);
      return entry != null && entry.schematic.inSchematic(x - entry.x, y - entry.y, z - entry.z, currentState);
   }

   @Override
   public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
      CompositeSchematicEntry entry = this.getSchematic(x, y, z, current);
      if (entry == null) {
         throw new IllegalStateException("couldn't find schematic for this position");
      } else {
         return entry.schematic.desiredState(x - entry.x, y - entry.y, z - entry.z, current, approxPlaceable);
      }
   }

   @Override
   public void reset() {
      for (CompositeSchematicEntry entry : this.schematicArr) {
         entry.schematic.reset();
      }
   }
}
