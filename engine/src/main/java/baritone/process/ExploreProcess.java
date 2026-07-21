package baritone.process;

import baritone.Baritone;
import baritone.api.cache.ICachedWorld;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.process.IExploreProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.MyChunkPos;
import baritone.utils.BaritoneProcessHelper;
import baritone.utils.NotificationHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public final class ExploreProcess extends BaritoneProcessHelper implements IExploreProcess {
   private BlockPos explorationOrigin;
   private ExploreProcess.IChunkFilter filter;
   private int distanceCompleted;

   public ExploreProcess(Baritone baritone) {
      super(baritone);
   }

   @Override
   public boolean isActive() {
      return this.explorationOrigin != null;
   }

   @Override
   public void explore(int centerX, int centerZ) {
      this.explorationOrigin = new BlockPos(centerX, 0, centerZ);
      this.distanceCompleted = 0;
   }

   @Override
   public void applyJsonFilter(Path path, boolean invert) throws Exception {
      this.filter = new ExploreProcess.JsonChunkFilter(path, invert);
   }

   public ExploreProcess.IChunkFilter calcFilter() {
      ExploreProcess.IChunkFilter filter;
      if (this.filter != null) {
         filter = new ExploreProcess.EitherChunk(this.filter, new ExploreProcess.BaritoneChunkCache());
      } else {
         filter = new ExploreProcess.BaritoneChunkCache();
      }

      return filter;
   }

   @Override
   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      if (calcFailed) {
         this.logDirect("Failed");
         if (this.baritone.settings().desktopNotifications.get() && this.baritone.settings().notificationOnExploreFinished.get()) {
            NotificationHelper.notify("Exploration failed", true);
         }

         this.onLostControl();
         return null;
      } else {
         ExploreProcess.IChunkFilter filter = this.calcFilter();
         if (!this.baritone.settings().disableCompletionCheck.get() && filter.countRemain() == 0) {
            this.logDirect("Explored all chunks");
            if (this.baritone.settings().desktopNotifications.get() && this.baritone.settings().notificationOnExploreFinished.get()) {
               NotificationHelper.notify("Explored all chunks", false);
            }

            this.onLostControl();
            return null;
         } else {
            Goal[] closestUncached = this.closestUncachedChunks(this.explorationOrigin, filter);
            if (closestUncached == null) {
               this.baritone.logDebug("awaiting region load from disk");
               return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            } else {
               return new PathingCommand(new GoalComposite(closestUncached), PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
            }
         }
      }
   }

   private Goal[] closestUncachedChunks(BlockPos center, ExploreProcess.IChunkFilter filter) {
      int chunkX = center.getX() >> 4;
      int chunkZ = center.getZ() >> 4;
      int count = Math.min(filter.countRemain(), this.baritone.settings().exploreChunkSetMinimumSize.get());
      List<BlockPos> centers = new ArrayList<>();
      int renderDistance = this.baritone.settings().worldExploringChunkOffset.get();
      int dist = this.distanceCompleted;

      while (true) {
         for (int dx = -dist; dx <= dist; dx++) {
            int zval = dist - Math.abs(dx);
            int mult = 0;

            while (mult < 2) {
               int dz = (mult * 2 - 1) * zval;
               int trueDist = Math.abs(dx) + Math.abs(dz);
               if (trueDist != dist) {
                  throw new IllegalStateException();
               }

               switch (filter.isAlreadyExplored(chunkX + dx, chunkZ + dz)) {
                  case UNKNOWN:
                     return null;
                  case NOT_EXPLORED:
                  default:
                     int centerX = (chunkX + dx << 4) + 8;
                     int centerZ = (chunkZ + dz << 4) + 8;
                     int offset = renderDistance << 4;
                     if (dx < 0) {
                        centerX -= offset;
                     } else {
                        centerX += offset;
                     }

                     if (dz < 0) {
                        centerZ -= offset;
                     } else {
                        centerZ += offset;
                     }

                     centers.add(new BlockPos(centerX, 0, centerZ));
                  case EXPLORED:
                     mult++;
               }
            }
         }

         if (dist % 10 == 0) {
            count = Math.min(filter.countRemain(), this.baritone.settings().exploreChunkSetMinimumSize.get());
         }

         if (centers.size() >= count) {
            return centers.stream().map(pos -> this.createGoal(pos.getX(), pos.getZ())).toArray(Goal[]::new);
         }

         if (centers.isEmpty()) {
            this.distanceCompleted = dist + 1;
         }

         dist++;
      }
   }

   private Goal createGoal(int x, int z) {
      return this.baritone.settings().exploreMaintainY.get() == -1 ? new GoalXZ(x, z) : new GoalXZ(x, z) {
         @Override
         public double heuristic(int x, int y, int zx) {
            return super.heuristic(x, y, zx) + GoalYLevel.calculate(ExploreProcess.this.baritone.settings().exploreMaintainY.get(), y);
         }
      };
   }

   @Override
   public void onLostControl() {
      this.explorationOrigin = null;
   }

   @Override
   public String displayName0() {
      return "Exploring around "
         + this.explorationOrigin
         + ", distance completed "
         + this.distanceCompleted
         + ", currently going to "
         + new GoalComposite(this.closestUncachedChunks(this.explorationOrigin, this.calcFilter()));
   }

   private class BaritoneChunkCache implements ExploreProcess.IChunkFilter {
      private final ICachedWorld cache = ExploreProcess.this.baritone.getWorldProvider().getCurrentWorld().getCachedWorld();

      @Override
      public ExploreProcess.Status isAlreadyExplored(int chunkX, int chunkZ) {
         int centerX = chunkX << 4;
         int centerZ = chunkZ << 4;
         return this.cache.isCached(centerX, centerZ) ? ExploreProcess.Status.EXPLORED : ExploreProcess.Status.NOT_EXPLORED;
      }

      @Override
      public int countRemain() {
         return Integer.MAX_VALUE;
      }
   }

   private class EitherChunk implements ExploreProcess.IChunkFilter {
      private final ExploreProcess.IChunkFilter a;
      private final ExploreProcess.IChunkFilter b;

      private EitherChunk(ExploreProcess.IChunkFilter a, ExploreProcess.IChunkFilter b) {
         this.a = a;
         this.b = b;
      }

      @Override
      public ExploreProcess.Status isAlreadyExplored(int chunkX, int chunkZ) {
         return this.a.isAlreadyExplored(chunkX, chunkZ) == ExploreProcess.Status.EXPLORED
            ? ExploreProcess.Status.EXPLORED
            : this.b.isAlreadyExplored(chunkX, chunkZ);
      }

      @Override
      public int countRemain() {
         return Math.min(this.a.countRemain(), this.b.countRemain());
      }
   }

   private interface IChunkFilter {
      ExploreProcess.Status isAlreadyExplored(int var1, int var2);

      int countRemain();
   }

   private class JsonChunkFilter implements ExploreProcess.IChunkFilter {
      private final boolean invert;
      private final LongOpenHashSet inFilter;
      private final MyChunkPos[] positions;

      private JsonChunkFilter(Path path, boolean invert) throws Exception {
         this.invert = invert;
         Gson gson = new GsonBuilder().create();
         this.positions = (MyChunkPos[])gson.fromJson(new InputStreamReader(Files.newInputStream(path)), MyChunkPos[].class);
         ExploreProcess.this.logDirect("Loaded " + this.positions.length + " positions");
         this.inFilter = new LongOpenHashSet();

         for (MyChunkPos mcp : this.positions) {
            this.inFilter.add(ChunkPos.asLong(mcp.x, mcp.z));
         }
      }

      @Override
      public ExploreProcess.Status isAlreadyExplored(int chunkX, int chunkZ) {
         return this.inFilter.contains(ChunkPos.asLong(chunkX, chunkZ)) ^ this.invert ? ExploreProcess.Status.EXPLORED : ExploreProcess.Status.UNKNOWN;
      }

      @Override
      public int countRemain() {
         if (!this.invert) {
            return Integer.MAX_VALUE;
         } else {
            int countRemain = 0;
            ExploreProcess.BaritoneChunkCache bcc = ExploreProcess.this.new BaritoneChunkCache();

            for (MyChunkPos pos : this.positions) {
               if (bcc.isAlreadyExplored(pos.x, pos.z) != ExploreProcess.Status.EXPLORED) {
                  if (++countRemain >= ExploreProcess.this.baritone.settings().exploreChunkSetMinimumSize.get()) {
                     return countRemain;
                  }
               }
            }

            return countRemain;
         }
      }
   }

   private static enum Status {
      EXPLORED,
      NOT_EXPLORED,
      UNKNOWN;
   }
}
