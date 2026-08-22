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

import com.player2.playerengine.automaton.api.event.events.type.EventState;

public final class ChunkEvent {
   private final EventState state;
   private final ChunkEvent.Type type;
   private final int x;
   private final int z;

   public ChunkEvent(EventState state, ChunkEvent.Type type, int x, int z) {
      this.state = state;
      this.type = type;
      this.x = x;
      this.z = z;
   }

   public final EventState getState() {
      return this.state;
   }

   public final ChunkEvent.Type getType() {
      return this.type;
   }

   public final int getX() {
      return this.x;
   }

   public final int getZ() {
      return this.z;
   }

   public static enum Type {
      LOAD,
      UNLOAD,
      POPULATE_FULL,
      POPULATE_PARTIAL;
   }
}
