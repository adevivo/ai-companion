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

package com.player2.playerengine.automaton.api.event.events;

import net.minecraft.core.BlockPos;

public final class BlockInteractEvent {
   private final BlockPos pos;
   private final BlockInteractEvent.Type type;

   public BlockInteractEvent(BlockPos pos, BlockInteractEvent.Type type) {
      this.pos = pos;
      this.type = type;
   }

   public final BlockPos getPos() {
      return this.pos;
   }

   public final BlockInteractEvent.Type getType() {
      return this.type;
   }

   public static enum Type {
      START_BREAK,
      USE;
   }
}
