package baritone.utils.pathing;

import baritone.api.pathing.calc.Avoidance;
import baritone.api.pathing.calc.IPath;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IEntityContext;
import baritone.pathing.movement.CalculationContext;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;

public final class Favoring {
   private final Long2DoubleOpenHashMap favorings = new Long2DoubleOpenHashMap();

   public Favoring(IEntityContext ctx, IPath previous, CalculationContext context) {
      this(previous, context);

      for (Avoidance avoid : ctx.listAvoidedAreas()) {
         avoid.applySpherical(this.favorings);
      }

      ctx.logDebug("Favoring size: " + this.favorings.size());
   }

   public Favoring(IPath previous, CalculationContext context) {
      this.favorings.defaultReturnValue(1.0);
      double coeff = context.backtrackCostFavoringCoefficient;
      if (coeff != 1.0 && previous != null) {
         previous.positions().forEach(pos -> this.favorings.put(BetterBlockPos.longHash(pos), coeff));
      }
   }

   public boolean isEmpty() {
      return this.favorings.isEmpty();
   }

   public double calculate(long hash) {
      return this.favorings.get(hash);
   }
}
