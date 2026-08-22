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

package com.player2.playerengine.automaton.api.command.datatypes;

import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.helpers.TabCompleteHelper;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

public enum EntityClassById implements IDatatypeFor<EntityType<?>> {
   INSTANCE;

   public EntityType<?> get(IDatatypeContext ctx) throws CommandException {
      Identifier id = Identifier.tryParse(ctx.getConsumer().getString());
      EntityType<?> entity;
      if ((entity = (EntityType<?>)BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null)) == null) {
         throw new IllegalArgumentException("no entity found by that id");
      } else {
         return entity;
      }
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
      return new TabCompleteHelper()
         .append(BuiltInRegistries.ENTITY_TYPE.stream().map(Object::toString))
         .filterPrefixNamespaced(ctx.getConsumer().getString())
         .sortAlphabetically()
         .stream();
   }
}
