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

import com.player2.playerengine.automaton.api.command.datatypes.IDatatype;
import com.player2.playerengine.automaton.api.command.datatypes.IDatatypeFor;
import com.player2.playerengine.automaton.api.command.datatypes.IDatatypePost;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.exception.CommandInvalidTypeException;
import com.player2.playerengine.automaton.api.command.exception.CommandNotEnoughArgumentsException;
import com.player2.playerengine.automaton.api.command.exception.CommandTooManyArgumentsException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.stream.Stream;

public interface IArgConsumer {
   LinkedList<ICommandArgument> getArgs();

   Deque<ICommandArgument> getConsumed();

   boolean has(int var1);

   boolean hasAny();

   boolean hasAtMost(int var1);

   boolean hasAtMostOne();

   boolean hasExactly(int var1);

   boolean hasExactlyOne();

   ICommandArgument peek(int var1) throws CommandNotEnoughArgumentsException;

   ICommandArgument peek() throws CommandNotEnoughArgumentsException;

   boolean is(Class<?> var1, int var2) throws CommandNotEnoughArgumentsException;

   boolean is(Class<?> var1) throws CommandNotEnoughArgumentsException;

   String peekString(int var1) throws CommandNotEnoughArgumentsException;

   String peekString() throws CommandNotEnoughArgumentsException;

   <E extends Enum<?>> E peekEnum(Class<E> var1, int var2) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException;

   <E extends Enum<?>> E peekEnum(Class<E> var1) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException;

   <E extends Enum<?>> E peekEnumOrNull(Class<E> var1, int var2) throws CommandNotEnoughArgumentsException;

   <E extends Enum<?>> E peekEnumOrNull(Class<E> var1) throws CommandNotEnoughArgumentsException;

   <T> T peekAs(Class<T> var1, int var2) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException;

   <T> T peekAs(Class<T> var1) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException;

   <T> T peekAsOrDefault(Class<T> var1, T var2, int var3) throws CommandNotEnoughArgumentsException;

   <T> T peekAsOrDefault(Class<T> var1, T var2) throws CommandNotEnoughArgumentsException;

   <T> T peekAsOrNull(Class<T> var1, int var2) throws CommandNotEnoughArgumentsException;

   <T> T peekAsOrNull(Class<T> var1) throws CommandNotEnoughArgumentsException;

   <T> T peekDatatype(IDatatypeFor<T> var1) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException;

   <T, O> T peekDatatype(IDatatypePost<T, O> var1) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException;

   <T, O> T peekDatatype(IDatatypePost<T, O> var1, O var2) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException;

   <T> T peekDatatypeOrNull(IDatatypeFor<T> var1);

   <T, O> T peekDatatypeOrNull(IDatatypePost<T, O> var1);

   <T, O, D extends IDatatypePost<T, O>> T peekDatatypePost(D var1, O var2) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException;

   <T, O, D extends IDatatypePost<T, O>> T peekDatatypePostOrDefault(D var1, O var2, T var3);

   <T, O, D extends IDatatypePost<T, O>> T peekDatatypePostOrNull(D var1, O var2);

   <T, D extends IDatatypeFor<T>> T peekDatatypeFor(Class<D> var1);

   <T, D extends IDatatypeFor<T>> T peekDatatypeForOrDefault(Class<D> var1, T var2);

   <T, D extends IDatatypeFor<T>> T peekDatatypeForOrNull(Class<D> var1);

   ICommandArgument get() throws CommandNotEnoughArgumentsException;

   String getString() throws CommandNotEnoughArgumentsException;

   <E extends Enum<?>> E getEnum(Class<E> var1) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException;

   <E extends Enum<?>> E getEnumOrDefault(Class<E> var1, E var2) throws CommandNotEnoughArgumentsException;

   <E extends Enum<?>> E getEnumOrNull(Class<E> var1) throws CommandNotEnoughArgumentsException;

   <T> T getAs(Class<T> var1) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException;

   <T> T getAsOrDefault(Class<T> var1, T var2) throws CommandNotEnoughArgumentsException;

   <T> T getAsOrNull(Class<T> var1) throws CommandNotEnoughArgumentsException;

   <T, O, D extends IDatatypePost<T, O>> T getDatatypePost(D var1, O var2) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException;

   <T, O, D extends IDatatypePost<T, O>> T getDatatypePostOrDefault(D var1, O var2, T var3);

   <T, O, D extends IDatatypePost<T, O>> T getDatatypePostOrNull(D var1, O var2);

   <T, D extends IDatatypeFor<T>> T getDatatypeFor(D var1) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException;

   <T, D extends IDatatypeFor<T>> T getDatatypeForOrDefault(D var1, T var2);

   <T, D extends IDatatypeFor<T>> T getDatatypeForOrNull(D var1);

   <T extends IDatatype> Stream<String> tabCompleteDatatype(T var1);

   String rawRest();

   void requireMin(int var1) throws CommandNotEnoughArgumentsException;

   void requireMax(int var1) throws CommandTooManyArgumentsException;

   void requireExactly(int var1) throws CommandException;

   boolean hasConsumed();

   ICommandArgument consumed();

   String consumedString();

   IArgConsumer copy();
}
