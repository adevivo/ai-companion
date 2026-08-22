package com.player2.playerengine.mixins.baritone;

import com.player2.playerengine.automaton.utils.accessor.ServerChunkManagerAccessor;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({ServerChunkCache.class})
public abstract class MixinServerChunkManager implements ServerChunkManagerAccessor {
   @Shadow
   @Nullable
   protected abstract ChunkHolder getVisibleChunkIfPresent(long var1);

   @Nullable
   @Override
   public LevelChunk automatone$getChunkNow(int chunkX, int chunkZ) {
      ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
      return chunkHolder == null ? null : chunkHolder.getTickingChunk();
   }
}
