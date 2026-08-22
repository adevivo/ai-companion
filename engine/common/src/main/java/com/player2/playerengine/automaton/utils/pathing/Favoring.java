/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.player2.playerengine.automaton.utils.pathing;

import com.player2.playerengine.automaton.api.pathing.calc.Avoidance;
import com.player2.playerengine.automaton.api.pathing.calc.IPath;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import com.player2.playerengine.automaton.pathing.movement.CalculationContext;
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
