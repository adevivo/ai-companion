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

package com.player2.playerengine.automaton.api.cache;

import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import java.util.Date;

public class Waypoint implements IWaypoint {
   private final String name;
   private final IWaypoint.Tag tag;
   private final long creationTimestamp;
   private final BetterBlockPos location;

   public Waypoint(String name, IWaypoint.Tag tag, BetterBlockPos location) {
      this(name, tag, location, System.currentTimeMillis());
   }

   public Waypoint(String name, IWaypoint.Tag tag, BetterBlockPos location, long creationTimestamp) {
      this.name = name;
      this.tag = tag;
      this.location = location;
      this.creationTimestamp = creationTimestamp;
   }

   @Override
   public int hashCode() {
      return this.name.hashCode() ^ this.tag.hashCode() ^ this.location.hashCode() ^ Long.hashCode(this.creationTimestamp);
   }

   @Override
   public String getName() {
      return this.name;
   }

   @Override
   public IWaypoint.Tag getTag() {
      return this.tag;
   }

   @Override
   public long getCreationTimestamp() {
      return this.creationTimestamp;
   }

   @Override
   public BetterBlockPos getLocation() {
      return this.location;
   }

   @Override
   public String toString() {
      return String.format("%s %s %s", this.name, BetterBlockPos.from(this.location).toString(), new Date(this.creationTimestamp).toString());
   }

   @Override
   public boolean equals(Object o) {
      if (o == null) {
         return false;
      } else {
         return !(o instanceof IWaypoint w) ? false : this.name.equals(w.getName()) && this.tag == w.getTag() && this.location.equals(w.getLocation());
      }
   }
}
