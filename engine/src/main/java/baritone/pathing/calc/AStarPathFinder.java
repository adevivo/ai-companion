package baritone.pathing.calc;

import baritone.PlayerEngine;
import baritone.api.Settings;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.Goal;
import baritone.api.utils.BetterBlockPos;
import baritone.pathing.calc.openset.BinaryHeapOpenSet;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Moves;
import baritone.utils.pathing.BetterWorldBorder;
import baritone.utils.pathing.Favoring;
import baritone.utils.pathing.MutableMoveResult;
import java.util.Optional;

public final class AStarPathFinder extends AbstractNodeCostSearch {
   private final Favoring favoring;
   private final CalculationContext calcContext;

   public AStarPathFinder(int startX, int startY, int startZ, Goal goal, Favoring favoring, CalculationContext context) {
      super(startX, startY, startZ, goal, context);
      this.favoring = favoring;
      this.calcContext = context;
   }

   @Override
   protected Optional<IPath> calculate0(long primaryTimeout, long failureTimeout) {
      this.startNode = this.getNodeAtPosition(this.startX, this.startY, this.startZ, BetterBlockPos.longHash(this.startX, this.startY, this.startZ));
      this.startNode.cost = 0.0;
      this.startNode.oxygenCost = this.calcContext.breathTime - this.calcContext.startingBreathTime;
      this.startNode.combinedCost = this.startNode.estimatedCostToGoal;
      BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
      openSet.insert(this.startNode);
      double[] bestHeuristicSoFar = new double[COEFFICIENTS.length];

      for (int i = 0; i < bestHeuristicSoFar.length; i++) {
         bestHeuristicSoFar[i] = this.startNode.estimatedCostToGoal;
         this.bestSoFar[i] = this.startNode;
      }

      MutableMoveResult res = new MutableMoveResult();
      BetterWorldBorder worldBorder = new BetterWorldBorder(this.calcContext.world.getWorldBorder());
      long startTime = System.currentTimeMillis();
      Settings settings = this.calcContext.getBaritone().settings();
      boolean slowPath = settings.slowPath.get();
      if (slowPath) {
         this.calcContext
            .baritone
            .logDebug("slowPath is on, path timeout will be " + settings.slowPathTimeoutMS.get() + "ms instead of " + primaryTimeout + "ms");
      }

      long primaryTimeoutTime = startTime + (slowPath ? settings.slowPathTimeoutMS.get() : primaryTimeout);
      long failureTimeoutTime = startTime + (slowPath ? settings.slowPathTimeoutMS.get() : failureTimeout);
      boolean failing = true;
      int numNodes = 0;
      int numMovementsConsidered = 0;
      int numEmptyChunk = 0;
      boolean isFavoring = !this.favoring.isEmpty();
      int timeCheckInterval = 64;
      int pathingMaxChunkBorderFetch = settings.pathingMaxChunkBorderFetch.get();
      double minimumImprovement = settings.minimumImprovementRepropagation.get() ? 0.01 : 0.0;
      Moves[] allMoves = Moves.values();

      while (!openSet.isEmpty() && numEmptyChunk < pathingMaxChunkBorderFetch && !this.cancelRequested) {
         if ((numNodes & timeCheckInterval - 1) == 0) {
            long now = System.currentTimeMillis();
            if (now - failureTimeoutTime >= 0L || !failing && now - primaryTimeoutTime >= 0L) {
               break;
            }
         }

         if (slowPath) {
            try {
               Thread.sleep(settings.slowPathTimeDelayMS.get());
            } catch (InterruptedException var44) {
            }
         }

         PathNode currentNode = openSet.removeLowest();
         this.mostRecentConsidered = currentNode;
         numNodes++;
         if (this.goal.isInGoal(currentNode.x, currentNode.y, currentNode.z)) {
            this.calcContext.baritone.logDebug("Took " + (System.currentTimeMillis() - startTime) + "ms, " + numMovementsConsidered + " movements considered");
            return Optional.of(new Path(this.startNode, currentNode, numNodes, this.goal, this.calcContext));
         }

         for (Moves moves : allMoves) {
            int newX = currentNode.x + moves.xOffset;
            int newZ = currentNode.z + moves.zOffset;
            if ((newX >> 4 != currentNode.x >> 4 || newZ >> 4 != currentNode.z >> 4) && !this.calcContext.isLoaded(newX, newZ)) {
               if (!moves.dynamicXZ) {
                  numEmptyChunk++;
               }
            } else if ((moves.dynamicXZ || worldBorder.entirelyContains(newX, newZ))
               && currentNode.y + moves.yOffset <= this.calcContext.worldTop
               && currentNode.y + moves.yOffset >= this.calcContext.worldBottom) {
               res.reset();
               moves.apply(this.calcContext, currentNode.x, currentNode.y, currentNode.z, res);
               numMovementsConsidered++;
               double actionCost = res.cost;
               if (!(actionCost >= 1000000.0) && !(res.oxygenCost + currentNode.oxygenCost >= this.calcContext.breathTime)) {
                  if (actionCost <= 0.0 || Double.isNaN(actionCost)) {
                     throw new IllegalStateException(moves + " calculated implausible cost " + actionCost);
                  }

                  if (!moves.dynamicXZ || worldBorder.entirelyContains(res.x, res.z)) {
                     if (!moves.dynamicXZ && (res.x != newX || res.z != newZ)) {
                        throw new IllegalStateException(moves + " " + res.x + " " + newX + " " + res.z + " " + newZ);
                     }

                     if (!moves.dynamicY && res.y != currentNode.y + moves.yOffset) {
                        throw new IllegalStateException(moves + " " + res.y + " " + (currentNode.y + moves.yOffset));
                     }

                     long hashCode = BetterBlockPos.longHash(res.x, res.y, res.z);
                     if (isFavoring) {
                        actionCost *= this.favoring.calculate(hashCode);
                     }

                     PathNode neighbor = this.getNodeAtPosition(res.x, res.y, res.z, hashCode);
                     double tentativeCost = currentNode.cost + actionCost;
                     if (neighbor.cost - tentativeCost > minimumImprovement) {
                        neighbor.previous = currentNode;
                        neighbor.cost = tentativeCost;
                        neighbor.oxygenCost = Math.max(0.0, currentNode.oxygenCost + res.oxygenCost);
                        neighbor.combinedCost = tentativeCost + neighbor.estimatedCostToGoal;
                        if (neighbor.isOpen()) {
                           openSet.update(neighbor);
                        } else {
                           openSet.insert(neighbor);
                        }

                        if (res.oxygenCost <= 0.0 || this.goal.isInGoal(neighbor.x, neighbor.y, neighbor.z)) {
                           for (int i = 0; i < COEFFICIENTS.length; i++) {
                              double heuristic = neighbor.estimatedCostToGoal + neighbor.cost / COEFFICIENTS[i];
                              if (bestHeuristicSoFar[i] - heuristic > minimumImprovement) {
                                 bestHeuristicSoFar[i] = heuristic;
                                 this.bestSoFar[i] = neighbor;
                                 if (failing && this.getDistFromStartSq(neighbor) > 25.0) {
                                    failing = false;
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      if (this.cancelRequested) {
         return Optional.empty();
      } else {
         PlayerEngine.LOGGER.debug(numMovementsConsidered + " movements considered");
         PlayerEngine.LOGGER.debug("Open set size: " + openSet.size());
         PlayerEngine.LOGGER.debug("PathNode map size: " + this.mapSize());
         PlayerEngine.LOGGER.debug((int)(numNodes * 1.0 / ((float)(System.currentTimeMillis() - startTime) / 1000.0F)) + " nodes per second");
         Optional<IPath> result = this.bestSoFar(true, numNodes);
         if (result.isPresent()) {
            this.calcContext.baritone.logDebug("Took " + (System.currentTimeMillis() - startTime) + "ms, " + numMovementsConsidered + " movements considered");
         }

         return result;
      }
   }
}
