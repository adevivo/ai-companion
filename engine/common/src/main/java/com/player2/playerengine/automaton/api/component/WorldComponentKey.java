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

package com.player2.playerengine.automaton.api.component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class WorldComponentKey<C> {
   private final Map<ResourceKey<Level>, C> storage = new HashMap<>();
   private final Function<Level, C> factory;

   public WorldComponentKey(Function<Level, C> factory) {
      this.factory = factory;
   }

   public final C get(Level provider) {
      return this.storage.computeIfAbsent(provider.dimension(), u -> this.factory.apply(provider));
   }
}
