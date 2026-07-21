package baritone.utils;

import baritone.api.utils.IEntityContext;
import baritone.utils.accessor.ServerChunkManagerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public class BlockStateInterface {
   private final ServerChunkManagerAccessor provider;
   protected final BlockGetter world;
   public final MutableBlockPos isPassableBlockPos;
   public final BlockGetter access;
   private LevelChunk prev = null;
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();

   public BlockStateInterface(IEntityContext ctx) {
      this(ctx.world());
   }

   public BlockStateInterface(Level world) {
      this.world = world;
      this.provider = (ServerChunkManagerAccessor)world.getChunkSource();
      this.isPassableBlockPos = new MutableBlockPos();
      this.access = new BlockStateInterfaceAccessWrapper(this);
   }

   public boolean worldContainsLoadedChunk(int blockX, int blockZ) {
      return this.provider.automatone$getChunkNow(blockX >> 4, blockZ >> 4) != null;
   }

   public static Block getBlock(IEntityContext ctx, BlockPos pos) {
      return get(ctx, pos).getBlock();
   }

   public static BlockState get(IEntityContext ctx, BlockPos pos) {
      return ctx.world().getBlockState(pos);
   }

   public BlockState get0(BlockPos pos) {
      return this.get0(pos.getX(), pos.getY(), pos.getZ());
   }

   public BlockState get0(int x, int y, int z) {
      if (this.world.isOutsideBuildHeight(y)) {
         return AIR;
      } else {
         LevelChunk cached = this.prev;
         if (cached != null && cached.getPos().x == x >> 4 && cached.getPos().z == z >> 4) {
            return getFromChunk(this.world, cached, x, y, z);
         } else {
            LevelChunk chunk = this.provider.automatone$getChunkNow(x >> 4, z >> 4);
            if (chunk != null && !chunk.isEmpty()) {
               this.prev = chunk;
               return getFromChunk(this.world, chunk, x, y, z);
            } else {
               return AIR;
            }
         }
      }
   }

   public boolean isLoaded(int x, int z) {
      LevelChunk prevChunk = this.prev;
      if (prevChunk != null && prevChunk.getPos().x == x >> 4 && prevChunk.getPos().z == z >> 4) {
         return true;
      } else {
         prevChunk = this.provider.automatone$getChunkNow(x >> 4, z >> 4);
         if (prevChunk != null && !prevChunk.isEmpty()) {
            this.prev = prevChunk;
            return true;
         } else {
            return false;
         }
      }
   }

   public static BlockState getFromChunk(BlockGetter world, ChunkAccess chunk, int x, int y, int z) {
      LevelChunkSection section = chunk.getSections()[world.getSectionIndex(y)];
      return section.hasOnlyAir() ? AIR : section.getBlockState(x & 15, y & 15, z & 15);
   }
}
