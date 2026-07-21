package baritone.api.pathing.calc;

import baritone.api.utils.BetterBlockPos;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;

public class Avoidance {
   private final int centerX;
   private final int centerY;
   private final int centerZ;
   private final double coefficient;
   private final int radius;
   private final int radiusSq;

   public Avoidance(BlockPos center, double coefficient, int radius) {
      this(center.getX(), center.getY(), center.getZ(), coefficient, radius);
   }

   public Avoidance(int centerX, int centerY, int centerZ, double coefficient, int radius) {
      this.centerX = centerX;
      this.centerY = centerY;
      this.centerZ = centerZ;
      this.coefficient = coefficient;
      this.radius = radius;
      this.radiusSq = radius * radius;
   }

   public double coefficient(int x, int y, int z) {
      int xDiff = x - this.centerX;
      int yDiff = y - this.centerY;
      int zDiff = z - this.centerZ;
      return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff <= this.radiusSq ? this.coefficient : 1.0;
   }

   public void applySpherical(Long2DoubleOpenHashMap map) {
      for (int x = -this.radius; x <= this.radius; x++) {
         for (int y = -this.radius; y <= this.radius; y++) {
            for (int z = -this.radius; z <= this.radius; z++) {
               if (x * x + y * y + z * z <= this.radius * this.radius) {
                  long hash = BetterBlockPos.longHash(this.centerX + x, this.centerY + y, this.centerZ + z);
                  map.put(hash, map.get(hash) * this.coefficient);
               }
            }
         }
      }
   }
}
