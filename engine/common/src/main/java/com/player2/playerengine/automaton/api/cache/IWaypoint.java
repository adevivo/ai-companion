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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public interface IWaypoint {
   String getName();

   IWaypoint.Tag getTag();

   long getCreationTimestamp();

   BetterBlockPos getLocation();

   public static enum Tag {
      HOME("home", "base"),
      DEATH("death"),
      BED("bed", "spawn"),
      USER("user");

      private static final List<IWaypoint.Tag> TAG_LIST = Collections.unmodifiableList(Arrays.asList(values()));
      public final String[] names;

      private Tag(String... names) {
         this.names = names;
      }

      public String getName() {
         return this.names[0];
      }

      public static IWaypoint.Tag getByName(String name) {
         for (IWaypoint.Tag action : values()) {
            for (String alias : action.names) {
               if (alias.equalsIgnoreCase(name)) {
                  return action;
               }
            }
         }

         return null;
      }

      public static String[] getAllNames() {
         Set<String> names = new HashSet<>();

         for (IWaypoint.Tag tag : values()) {
            names.addAll(Arrays.asList(tag.names));
         }

         return names.toArray(new String[0]);
      }
   }
}
