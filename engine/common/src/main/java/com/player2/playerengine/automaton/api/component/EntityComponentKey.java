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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public class EntityComponentKey<C> {
   private final Map<UUID, C> storage = new HashMap<>();
   private final Function<LivingEntity, C> factory;

   public EntityComponentKey(Function<LivingEntity, C> factory) {
      this.factory = factory;
   }

   @Nullable
   public C getNullable(Object object) {
      if (object instanceof LivingEntity provider) {
         return this.storage.get(provider.getUUID()) == null ? null : this.storage.get(provider.getUUID());
      } else {
         return null;
      }
   }

   public final C get(Object object) {
      if (object instanceof LivingEntity provider) {
         return this.storage.computeIfAbsent(provider.getUUID(), u -> this.factory.apply(provider));
      } else {
         throw new NoSuchElementException();
      }
   }

   public final Optional<C> maybeGet(@Nullable Object object) {
      if (object instanceof LivingEntity provider) {
         return this.storage.get(provider.getUUID()) == null ? Optional.empty() : Optional.of(this.storage.get(provider.getUUID()));
      } else {
         return Optional.empty();
      }
   }
}
