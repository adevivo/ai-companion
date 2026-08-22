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

import java.util.function.Function;

public final class TickEvent {
   private static int overallTickCount;
   private final TickEvent.Type type;
   private final int count;

   public TickEvent(TickEvent.Type type, int count) {
      this.type = type;
      this.count = count;
   }

   public int getCount() {
      return this.count;
   }

   public TickEvent.Type getType() {
      return this.type;
   }

   public static synchronized Function<TickEvent.Type, TickEvent> createNextProvider() {
      int count = overallTickCount++;
      return type -> new TickEvent(type, count);
   }

   public static enum Type {
      IN,
      OUT;
   }
}
