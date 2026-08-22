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

package com.player2.playerengine.automaton.api.process;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public interface IFarmProcess extends IBaritoneProcess {
   void farm(int var1, BlockPos var2);

   /**
    * Why the farm is not currently making progress, or {@code null} when it is working.
    *
    * <p>Exists so the layer above can tell the owner the truth. A farm task reports {@code <Farming ...>}
    * for as long as it is armed, whether or not anything is happening, so an agent reading only the task
    * status will confidently narrate a harvest that is not occurring — which is exactly what happened on
    * 2026-07-28. "Waiting for the crops to regrow" and "stuck with no block scan" both look identical
    * from outside and need very different responses.
    */
   @Nullable
   default String getStallReason() {
      return null;
   }

   default void farm() {
      this.farm(0, null);
   }

   default void farm(int range) {
      this.farm(range, null);
   }
}
