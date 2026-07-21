package baritone.pathing.calc;

import baritone.PlayerEngine;
import baritone.api.Settings;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.calc.IPathFinder;
import baritone.api.pathing.goals.Goal;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.PathCalculationResult;
import baritone.pathing.movement.CalculationContext;
import baritone.utils.NotificationHelper;
import baritone.utils.pathing.PathBase;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Optional;

public abstract class AbstractNodeCostSearch implements IPathFinder {
   protected final int startX;
   protected final int startY;
   protected final int startZ;
   protected final Goal goal;
   private final CalculationContext context;
   private final Long2ObjectOpenHashMap<PathNode> map;
   protected PathNode startNode;
   protected PathNode mostRecentConsidered;
   protected final PathNode[] bestSoFar;
   private volatile boolean isFinished;
   protected boolean cancelRequested;
   protected static final double[] COEFFICIENTS = new double[]{1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 10.0};
   protected static final double MIN_DIST_PATH = 5.0;
   protected static final double MIN_IMPROVEMENT = 0.01;

   AbstractNodeCostSearch(int startX, int startY, int startZ, Goal goal, CalculationContext context) {
      this.bestSoFar = new PathNode[COEFFICIENTS.length];
      this.startX = startX;
      this.startY = startY;
      this.startZ = startZ;
      this.goal = goal;
      this.context = context;
      this.map = new Long2ObjectOpenHashMap(context.baritone.settings().pathingMapDefaultSize.get(), context.baritone.settings().pathingMapLoadFactor.get());
   }

   public void cancel() {
      this.cancelRequested = true;
   }

   @Override
   public synchronized PathCalculationResult calculate(long primaryTimeout, long failureTimeout) {
      if (this.isFinished) {
         throw new IllegalStateException("Path finder cannot be reused!");
      } else {
         this.cancelRequested = false;

         PathCalculationResult var8;
         try {
            IPath path = this.calculate0(primaryTimeout, failureTimeout).map(IPath::postProcess).orElse(null);
            if (this.cancelRequested) {
               return new PathCalculationResult(PathCalculationResult.Type.CANCELLATION);
            }

            if (path == null) {
               return new PathCalculationResult(PathCalculationResult.Type.FAILURE);
            }

            int previousLength = path.length();
            Settings settings = this.context.getBaritone().settings();
            PathBase var14 = ((PathBase)path).cutoffAtLoadedChunks(this.context.bsi, settings);
            if (var14.length() < previousLength) {
               this.context.baritone.logDebug("Cutting off path at edge of loaded chunks");
               this.context.baritone.logDebug("Length decreased by " + (previousLength - var14.length()));
            } else {
               this.context.baritone.logDebug("Path ends within loaded chunks");
            }

            previousLength = var14.length();
            IPath var15 = var14.staticCutoff(this.goal, settings);
            if (var15.length() < previousLength) {
               this.context.baritone.logDebug("Static cutoff " + previousLength + " to " + var15.length());
            }

            if (!this.goal.isInGoal(var15.getDest())) {
               return new PathCalculationResult(PathCalculationResult.Type.SUCCESS_SEGMENT, var15);
            }

            var8 = new PathCalculationResult(PathCalculationResult.Type.SUCCESS_TO_GOAL, var15);
         } catch (Exception var12) {
            this.context.baritone.logDirect("Pathing exception: " + var12);
            PlayerEngine.LOGGER.error("Pathing exception: ", var12);
            return new PathCalculationResult(PathCalculationResult.Type.EXCEPTION);
         } finally {
            this.isFinished = true;
         }

         return var8;
      }
   }

   protected abstract Optional<IPath> calculate0(long var1, long var3);

   protected double getDistFromStartSq(PathNode n) {
      int xDiff = n.x - this.startX;
      int yDiff = n.y - this.startY;
      int zDiff = n.z - this.startZ;
      return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff;
   }

   protected PathNode getNodeAtPosition(int x, int y, int z, long hashCode) {
      PathNode node = (PathNode)this.map.get(hashCode);
      if (node == null) {
         node = new PathNode(x, y, z, this.goal);
         this.map.put(hashCode, node);
      }

      return node;
   }

   @Override
   public Optional<IPath> pathToMostRecentNodeConsidered() {
      return Optional.ofNullable(this.mostRecentConsidered).map(node -> new Path(this.startNode, node, 0, this.goal, this.context));
   }

   @Override
   public Optional<IPath> bestPathSoFar() {
      return this.bestSoFar(false, 0);
   }

   protected Optional<IPath> bestSoFar(boolean logInfo, int numNodes) {
      if (this.startNode == null) {
         return Optional.empty();
      } else {
         double bestDist = 0.0;

         for (int i = 0; i < COEFFICIENTS.length; i++) {
            if (this.bestSoFar[i] != null) {
               double dist = this.getDistFromStartSq(this.bestSoFar[i]);
               if (dist > bestDist) {
                  bestDist = dist;
               }

               if (dist > 25.0) {
                  if (logInfo) {
                     if (COEFFICIENTS[i] >= 3.0) {
                        PlayerEngine.LOGGER.warn("Warning: cost coefficient is greater than three! Probably means that");
                        PlayerEngine.LOGGER.warn("the path I found is pretty terrible (like sneak-bridging for dozens of blocks)");
                        PlayerEngine.LOGGER.warn("But I'm going to do it anyway, because yolo");
                     }

                     PlayerEngine.LOGGER.info("Path goes for " + Math.sqrt(dist) + " blocks");
                     this.context.baritone.logDebug("A* cost coefficient " + COEFFICIENTS[i]);
                  }

                  return Optional.of(new Path(this.startNode, this.bestSoFar[i], numNodes, this.goal, this.context));
               }
            }
         }

         if (logInfo) {
            this.context
               .baritone
               .logDebug(
                  "Even with a cost coefficient of " + COEFFICIENTS[COEFFICIENTS.length - 1] + ", I couldn't get more than " + Math.sqrt(bestDist) + " blocks"
               );
            this.context.baritone.logDebug("No path found =(");
            if (this.context.baritone.settings().desktopNotifications.get()) {
               NotificationHelper.notify("No path found =(", true);
            }
         }

         return Optional.empty();
      }
   }

   @Override
   public final boolean isFinished() {
      return this.isFinished;
   }

   @Override
   public final Goal getGoal() {
      return this.goal;
   }

   public BetterBlockPos getStart() {
      return new BetterBlockPos(this.startX, this.startY, this.startZ);
   }

   protected int mapSize() {
      return this.map.size();
   }
}
