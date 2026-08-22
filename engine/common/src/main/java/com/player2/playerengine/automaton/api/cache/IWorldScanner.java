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

package com.player2.playerengine.automaton.api.cache;

import com.player2.playerengine.automaton.api.utils.BlockOptionalMetaLookup;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

public interface IWorldScanner {
   List<BlockPos> scanChunkRadius(IEntityContext var1, BlockOptionalMetaLookup var2, int var3, int var4, int var5);

   default List<BlockPos> scanChunkRadius(IEntityContext ctx, List<Block> filter, int max, int yLevelThreshold, int maxSearchRadius) {
      return this.scanChunkRadius(ctx, new BlockOptionalMetaLookup(ctx.world(), filter.toArray(new Block[0])), max, yLevelThreshold, maxSearchRadius);
   }

   List<BlockPos> scanChunk(IEntityContext var1, BlockOptionalMetaLookup var2, ChunkPos var3, int var4, int var5);

   default List<BlockPos> scanChunk(IEntityContext ctx, List<Block> blocks, ChunkPos pos, int max, int yLevelThreshold) {
      return this.scanChunk(ctx, new BlockOptionalMetaLookup(ctx.world(), blocks), pos, max, yLevelThreshold);
   }

   int repack(IEntityContext var1);

   int repack(IEntityContext var1, int var2);
}
