package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.tasksystem.Task;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.Load;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

abstract class ChunkSearchTask extends Task {
   private final BlockPos startPoint;
   private final Object searchMutex = new Object();
   private final Set<ChunkPos> consideredAlready = new HashSet<>();
   private final Set<ChunkPos> searchedAlready = new HashSet<>();
   private final ArrayList<ChunkPos> searchLater = new ArrayList<>();
   private final ArrayList<ChunkPos> justLoaded = new ArrayList<>();
   private boolean first = true;
   private boolean finished = false;

   public ChunkSearchTask(BlockPos startPoint) {
      this.startPoint = startPoint;
   }

   public ChunkSearchTask(ChunkPos chunkPos) {
      this(chunkPos.getWorldPosition().offset(1, 1, 1));
   }

   public Set<ChunkPos> getSearchedChunks() {
      return this.searchedAlready;
   }

   public boolean finished() {
      return this.finished;
   }

   @Override
   protected void onStart() {
      if (this.first) {
         this.finished = false;
         this.first = false;
         ChunkPos startPos = this.controller.getWorld().getChunk(this.startPoint).getPos();
         synchronized (this.searchMutex) {
            this.searchChunkOrQueueSearch(this.controller, startPos);
         }
      }

      ServerChunkEvents.CHUNK_LOAD.register((Load)(evt, chunk) -> {
         if (chunk != null) {
            synchronized (this.searchMutex) {
               if (!this.searchedAlready.contains(chunk.getPos())) {
                  this.justLoaded.add(chunk.getPos());
               }
            }
         }
      });
   }

   @Override
   protected Task onTick() {
      synchronized (this.searchMutex) {
         if (!this.justLoaded.isEmpty()) {
            for (ChunkPos justLoaded : this.justLoaded) {
               if (this.searchLater.contains(justLoaded) && this.trySearchChunk(this.controller, justLoaded)) {
                  this.searchLater.remove(justLoaded);
               }
            }
         }

         this.justLoaded.clear();
      }

      ChunkPos closest = this.getBestChunk(this.controller, this.searchLater);
      if (closest == null) {
         this.finished = true;
         Debug.logWarning("Failed to find any chunks to go to. If we finish, that means we scanned all possible chunks.");
         return null;
      } else {
         return new GetToChunkTask(closest);
      }
   }

   protected ChunkPos getBestChunk(AltoClefController mod, List<ChunkPos> chunks) {
      double lowestScore = Double.POSITIVE_INFINITY;
      ChunkPos bestChunk = null;
      if (!chunks.isEmpty()) {
         for (ChunkPos toSearch : chunks) {
            double cx = (toSearch.getMinBlockX() + toSearch.getMaxBlockX() + 1) / 2.0;
            double cz = (toSearch.getMinBlockZ() + toSearch.getMaxBlockZ() + 1) / 2.0;
            double px = mod.getPlayer().getX();
            double pz = mod.getPlayer().getZ();
            double distanceSq = (cx - px) * (cx - px) + (cz - pz) * (cz - pz);
            double distanceToCenterSq = new Vec3(this.startPoint.getX() - cx, 0.0, this.startPoint.getZ() - cz).lengthSqr();
            double score = distanceSq + distanceToCenterSq * 0.8;
            if (score < lowestScore) {
               lowestScore = score;
               bestChunk = toSearch;
            }
         }
      }

      return bestChunk;
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   public boolean isFinished() {
      return this.searchLater.size() == 0;
   }

   @Override
   protected boolean isEqual(Task other) {
      if (other instanceof ChunkSearchTask task) {
         return !task.startPoint.equals(this.startPoint) ? false : this.isChunkSearchEqual(task);
      } else {
         return false;
      }
   }

   private void searchChunkOrQueueSearch(AltoClefController mod, ChunkPos pos) {
      if (!this.consideredAlready.contains(pos)) {
         this.consideredAlready.add(pos);
         if (!this.trySearchChunk(mod, pos) && !this.searchedAlready.contains(pos)) {
            this.searchLater.add(pos);
         }
      }
   }

   private boolean trySearchChunk(AltoClefController mod, ChunkPos pos) {
      if (this.searchedAlready.contains(pos)) {
         return true;
      } else if (mod.getChunkTracker().isChunkLoaded(pos)) {
         this.searchedAlready.add(pos);
         if (this.isChunkPartOfSearchSpace(mod, pos)) {
            this.searchChunkOrQueueSearch(mod, new ChunkPos(pos.x + 1, pos.z));
            this.searchChunkOrQueueSearch(mod, new ChunkPos(pos.x - 1, pos.z));
            this.searchChunkOrQueueSearch(mod, new ChunkPos(pos.x, pos.z + 1));
            this.searchChunkOrQueueSearch(mod, new ChunkPos(pos.x, pos.z - 1));
         }

         return true;
      } else {
         return false;
      }
   }

   protected abstract boolean isChunkPartOfSearchSpace(AltoClefController var1, ChunkPos var2);

   protected abstract boolean isChunkSearchEqual(ChunkSearchTask var1);
}
