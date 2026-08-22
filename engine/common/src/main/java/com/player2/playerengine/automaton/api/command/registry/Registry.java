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

package com.player2.playerengine.automaton.api.command.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Registry<V> {
   private final Deque<V> _entries = new LinkedList<>();
   private final Set<V> registered = new HashSet<>();
   public final Collection<V> entries = Collections.unmodifiableCollection(this._entries);

   public boolean registered(V entry) {
      return this.registered.contains(entry);
   }

   public boolean register(V entry) {
      if (!this.registered(entry)) {
         this._entries.addFirst(entry);
         this.registered.add(entry);
         return true;
      } else {
         return false;
      }
   }

   public void unregister(V entry) {
      if (!this.registered(entry)) {
         this._entries.remove(entry);
         this.registered.remove(entry);
      }
   }

   public Iterator<V> iterator() {
      return this._entries.iterator();
   }

   public Iterator<V> descendingIterator() {
      return this._entries.descendingIterator();
   }

   public Stream<V> stream() {
      return this._entries.stream();
   }

   public Stream<V> descendingStream() {
      return StreamSupport.stream(Spliterators.spliterator(this.descendingIterator(), (long)this._entries.size(), 16448), false);
   }
}
