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

package com.player2.playerengine.automaton.api.selection;

import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;

public interface ISelection {
   BetterBlockPos pos1();

   BetterBlockPos pos2();

   BetterBlockPos min();

   BetterBlockPos max();

   Vec3i size();

   AABB aabb();

   ISelection expand(Direction var1, int var2);

   ISelection contract(Direction var1, int var2);

   ISelection shift(Direction var1, int var2);
}
