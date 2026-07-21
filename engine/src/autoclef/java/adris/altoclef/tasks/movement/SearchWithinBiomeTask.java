package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.multiversion.world.WorldVer;
import adris.altoclef.tasksystem.Task;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

public class SearchWithinBiomeTask extends SearchChunksExploreTask {
   private final ResourceKey<Biome> toSearch;

   public SearchWithinBiomeTask(ResourceKey<Biome> toSearch) {
      this.toSearch = toSearch;
   }

   @Override
   protected boolean isChunkWithinSearchSpace(AltoClefController mod, ChunkPos pos) {
      return WorldVer.isBiomeAtPos(mod.getWorld(), this.toSearch, pos.getWorldPosition().offset(1, 1, 1));
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof SearchWithinBiomeTask task ? task.toSearch == this.toSearch : false;
   }

   @Override
   protected String toDebugString() {
      return "Searching for+within biome: " + this.toSearch;
   }
}
