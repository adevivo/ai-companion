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

import com.player2.playerengine.automaton.api.Settings;
import com.player2.playerengine.automaton.api.pathing.calc.IPath;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.pathing.path.CutoffPath;
import com.player2.playerengine.automaton.utils.BlockStateInterface;
import net.minecraft.core.BlockPos;

public abstract class PathBase implements IPath {
   public PathBase cutoffAtLoadedChunks(BlockStateInterface bsi, Settings settings) {
      if (!settings.cutoffAtLoadBoundary.get()) {
         return this;
      } else {
         for (int i = 0; i < this.positions().size(); i++) {
            BlockPos pos = this.positions().get(i);
            if (!bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())) {
               return new CutoffPath(this, i);
            }
         }

         return this;
      }
   }

   public PathBase staticCutoff(Goal destination, Settings settings) {
      int minLength = settings.pathCutoffMinimumLength.get();
      double cutoffFactor = settings.pathCutoffFactor.get();
      if (this.length() < minLength) {
         return this;
      } else if (destination != null && !destination.isInGoal(this.getDest())) {
         int newLength = (int)((this.length() - minLength) * cutoffFactor) + minLength - 1;
         return new CutoffPath(this, newLength);
      } else {
         return this;
      }
   }
}
