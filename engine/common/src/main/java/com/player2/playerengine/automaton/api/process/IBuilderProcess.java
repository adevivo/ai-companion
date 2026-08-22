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

package com.player2.playerengine.automaton.api.process;

import com.player2.playerengine.automaton.api.schematic.ISchematic;
import com.player2.playerengine.automaton.utils.DirUtil;
import java.io.File;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

public interface IBuilderProcess extends IBaritoneProcess {
   void build(String var1, ISchematic var2, Vec3i var3);

   boolean build(String var1, File var2, Vec3i var3);

   default boolean build(String schematicFile, BlockPos origin) {
      File file = DirUtil.getGameDir().resolve("schematics").resolve(schematicFile).toFile();
      return this.build(schematicFile, file, origin);
   }

   void buildOpenSchematic();

   void pause();

   boolean isPaused();

   void resume();

   void clearArea(BlockPos var1, BlockPos var2);

   List<BlockState> getApproxPlaceable();
}
