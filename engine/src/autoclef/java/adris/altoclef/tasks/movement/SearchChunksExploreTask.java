package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.tasksystem.Task;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.Load;
import net.minecraft.world.level.ChunkPos;

public abstract class SearchChunksExploreTask extends Task {
   private final Object searcherMutex = new Object();
   private final Set<ChunkPos> alreadyExplored = new HashSet<>();
   private ChunkSearchTask searcher;

   protected ChunkPos getBestChunkOverride(AltoClefController mod, List<ChunkPos> chunks) {
      return null;
   }

   @Override
   protected void onStart() {
      ServerChunkEvents.CHUNK_LOAD.register((Load)(evt, chunk) -> this.onChunkLoad(chunk.getPos()));
      this.resetSearch();
   }

   @Override
   protected Task onTick() {
      synchronized (this.searcherMutex) {
         if (this.searcher == null) {
            this.setDebugState("Exploring/Searching for valid chunk");
            return this.getWanderTask();
         } else {
            if (this.searcher.isActive() && this.searcher.isFinished()) {
               Debug.logWarning("Target object search failed.");
               this.alreadyExplored.addAll(this.searcher.getSearchedChunks());
               this.searcher = null;
            } else if (this.searcher.finished()) {
               this.setDebugState("Searching for target object...");
               Debug.logMessage("Search finished.");
               this.alreadyExplored.addAll(this.searcher.getSearchedChunks());
               this.searcher = null;
            }

            this.setDebugState("Searching within chunks...");
            return this.searcher;
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   private void onChunkLoad(ChunkPos pos) {
      if (this.searcher == null) {
         if (this.isActive()) {
            if (this.isChunkWithinSearchSpace(this.controller, pos)) {
               synchronized (this.searcherMutex) {
                  if (!this.alreadyExplored.contains(pos)) {
                     Debug.logMessage("New searcher: " + pos);
                     this.searcher = new SearchChunksExploreTask.SearchSubTask(pos);
                  }
               }
            }
         }
      }
   }

   protected Task getWanderTask() {
      return new TimeoutWanderTask(true);
   }

   public boolean failedSearch() {
      return this.searcher == null;
   }

   public void resetSearch() {
      this.searcher = null;
      this.alreadyExplored.clear();

      for (ChunkPos start : this.controller.getChunkTracker().getLoadedChunks()) {
         this.onChunkLoad(start);
      }
   }

   protected abstract boolean isChunkWithinSearchSpace(AltoClefController var1, ChunkPos var2);

   class SearchSubTask extends ChunkSearchTask {
      public SearchSubTask(ChunkPos start) {
         super(start);
      }

      @Override
      protected boolean isChunkPartOfSearchSpace(AltoClefController mod, ChunkPos pos) {
         return SearchChunksExploreTask.this.isChunkWithinSearchSpace(mod, pos);
      }

      @Override
      public ChunkPos getBestChunk(AltoClefController mod, List<ChunkPos> chunks) {
         ChunkPos override = SearchChunksExploreTask.this.getBestChunkOverride(mod, chunks);
         return override != null ? override : super.getBestChunk(mod, chunks);
      }

      @Override
      protected boolean isChunkSearchEqual(ChunkSearchTask other) {
         return other == this;
      }

      @Override
      protected String toDebugString() {
         return "Searching chunks...";
      }
   }
}
