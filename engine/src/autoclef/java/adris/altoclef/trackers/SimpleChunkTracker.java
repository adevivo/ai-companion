package adris.altoclef.trackers;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.Load;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.Unload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.EmptyLevelChunk;

public class SimpleChunkTracker {
   private final AltoClefController mod;
   private final Set<ChunkPos> loaded = new HashSet<>();

   public SimpleChunkTracker(AltoClefController mod) {
      this.mod = mod;
      ServerChunkEvents.CHUNK_LOAD.register((Load)(evt, chunk) -> this.onLoad(chunk.getPos()));
      ServerChunkEvents.CHUNK_UNLOAD.register((Unload)(evt, chunk) -> this.onUnload(chunk.getPos()));
   }

   private void onLoad(ChunkPos pos) {
      this.loaded.add(pos);
   }

   private void onUnload(ChunkPos pos) {
      this.loaded.remove(pos);
   }

   public boolean isChunkLoaded(ChunkPos pos) {
      return !(this.mod.getWorld().getChunk(pos.x, pos.z) instanceof EmptyLevelChunk);
   }

   public boolean isChunkLoaded(BlockPos pos) {
      return this.isChunkLoaded(new ChunkPos(pos));
   }

   public List<ChunkPos> getLoadedChunks() {
      List<ChunkPos> result = new ArrayList<>(this.loaded);
      return result.stream().filter(this::isChunkLoaded).distinct().collect(Collectors.toList());
   }

   public boolean scanChunk(ChunkPos chunk, Predicate<BlockPos> onBlockStop) {
      if (!this.isChunkLoaded(chunk)) {
         return false;
      } else {
         int bottomY = this.mod.getWorld().getMinBuildHeight();
         int topY = this.mod.getWorld().getMaxBuildHeight();

         for (int xx = chunk.getMinBlockX(); xx <= chunk.getMaxBlockX(); xx++) {
            for (int yy = bottomY; yy <= topY; yy++) {
               for (int zz = chunk.getMinBlockZ(); zz <= chunk.getMaxBlockZ(); zz++) {
                  if (onBlockStop.test(new BlockPos(xx, yy, zz))) {
                     return true;
                  }
               }
            }
         }

         return false;
      }
   }

   public void scanChunk(ChunkPos chunk, Consumer<BlockPos> onBlock) {
      this.scanChunk(chunk, block -> {
         onBlock.accept(block);
         return false;
      });
   }

   public void reset(AltoClefController mod) {
      Debug.logInternal("CHUNKS RESET");
      this.loaded.clear();
   }
}
