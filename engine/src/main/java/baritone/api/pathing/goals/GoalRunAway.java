package baritone.api.pathing.goals;

import baritone.api.utils.SettingsUtil;
import it.unimi.dsi.fastutil.doubles.DoubleIterator;
import it.unimi.dsi.fastutil.doubles.DoubleOpenHashSet;
import java.util.Arrays;
import net.minecraft.core.BlockPos;

public class GoalRunAway implements Goal {
   private final BlockPos[] from;
   private final int distanceSq;
   private final Integer maintainY;

   public GoalRunAway(double distance, BlockPos... from) {
      this(distance, null, from);
   }

   public GoalRunAway(double distance, Integer maintainY, BlockPos... from) {
      if (from.length == 0) {
         throw new IllegalArgumentException();
      } else {
         this.from = from;
         this.distanceSq = (int)(distance * distance);
         this.maintainY = maintainY;
      }
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      if (this.maintainY != null && this.maintainY != y) {
         return false;
      } else {
         for (BlockPos p : this.from) {
            int diffX = x - p.getX();
            int diffZ = z - p.getZ();
            int distSq = diffX * diffX + diffZ * diffZ;
            if (distSq < this.distanceSq) {
               return false;
            }
         }

         return true;
      }
   }

   @Override
   public double heuristic(int x, int y, int z) {
      double min = Double.MAX_VALUE;

      for (BlockPos p : this.from) {
         double h = GoalXZ.calculate(p.getX() - x, p.getZ() - z);
         if (h < min) {
            min = h;
         }
      }

      min = -min;
      if (this.maintainY != null) {
         min = min * 0.6 + GoalYLevel.calculate(this.maintainY, y) * 1.5;
      }

      return min;
   }

   @Override
   public double heuristic() {
      int distance = (int)Math.ceil(Math.sqrt(this.distanceSq));
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;

      for (BlockPos p : this.from) {
         minX = Math.min(minX, p.getX() - distance);
         minY = Math.min(minY, p.getY() - distance);
         minZ = Math.min(minZ, p.getZ() - distance);
         maxX = Math.max(minX, p.getX() + distance);
         maxY = Math.max(minY, p.getY() + distance);
         maxZ = Math.max(minZ, p.getZ() + distance);
      }

      DoubleOpenHashSet maybeAlwaysInside = new DoubleOpenHashSet();
      double minOutside = Double.POSITIVE_INFINITY;

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               double h = this.heuristic(x, y, z);
               if (h < minOutside && this.isInGoal(x, y, z)) {
                  maybeAlwaysInside.add(h);
               } else {
                  minOutside = Math.min(minOutside, h);
               }
            }
         }
      }

      double maxInside = Double.NEGATIVE_INFINITY;
      DoubleIterator it = maybeAlwaysInside.iterator();

      while (it.hasNext()) {
         double inside = it.nextDouble();
         if (inside < minOutside) {
            maxInside = Math.max(maxInside, inside);
         }
      }

      return maxInside;
   }

   @Override
   public String toString() {
      return this.maintainY != null
         ? String.format("GoalRunAwayFromMaintainY y=%s, %s", SettingsUtil.maybeCensor(this.maintainY), Arrays.asList(this.from))
         : "GoalRunAwayFrom" + Arrays.<BlockPos>asList(this.from);
   }
}
