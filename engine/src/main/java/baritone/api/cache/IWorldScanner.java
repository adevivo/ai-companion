package baritone.api.cache;

import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.IEntityContext;
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
