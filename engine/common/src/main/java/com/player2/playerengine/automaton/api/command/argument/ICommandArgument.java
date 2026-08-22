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

package com.player2.playerengine.automaton.api.command.argument;

import com.player2.playerengine.automaton.api.command.exception.CommandInvalidTypeException;

public interface ICommandArgument {
   int getIndex();

   String getValue();

   String getRawRest();

   <E extends Enum<?>> E getEnum(Class<E> var1) throws CommandInvalidTypeException;

   <T> T getAs(Class<T> var1) throws CommandInvalidTypeException;

   <T> boolean is(Class<T> var1);

   <T, S> T getAs(Class<T> var1, Class<S> var2, S var3) throws CommandInvalidTypeException;

   <T, S> boolean is(Class<T> var1, Class<S> var2, S var3);
}
