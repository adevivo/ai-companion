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

package com.player2.playerengine.automaton.command.argparser;

import com.player2.playerengine.automaton.api.command.argparser.IArgParser;
import com.player2.playerengine.automaton.api.command.argparser.IArgParserManager;
import com.player2.playerengine.automaton.api.command.argument.ICommandArgument;
import com.player2.playerengine.automaton.api.command.exception.CommandInvalidTypeException;
import com.player2.playerengine.automaton.api.command.exception.CommandNoParserForTypeException;
import com.player2.playerengine.automaton.api.command.registry.Registry;

public enum ArgParserManager implements IArgParserManager {
   INSTANCE;

   public final Registry<IArgParser<?>> registry = new Registry<>();

   private ArgParserManager() {
      DefaultArgParsers.ALL.forEach(this.registry::register);
   }

   @Override
   public <T> IArgParser.Stateless<T> getParserStateless(Class<T> type) {
      return this.registry
         .descendingStream()
         .filter(IArgParser.Stateless.class::isInstance)
         .filter(parser -> parser.getTarget().isAssignableFrom(type))
         .map(p -> (IArgParser.Stateless<T>)p)
         .findFirst()
         .orElse(null);
   }

   @Override
   public <T, S> IArgParser.Stated<T, S> getParserStated(Class<T> type, Class<S> stateKlass) {
      return this.registry
         .descendingStream()
         .filter(IArgParser.Stated.class::isInstance)
         .map(obj -> (IArgParser.Stated)obj)
         .filter(parser -> parser.getTarget().isAssignableFrom(type))
         .filter(parser -> parser.getStateType().isAssignableFrom(stateKlass))
         .map(p -> (IArgParser.Stated<T, S>)p)
         .findFirst()
         .orElse(null);
   }

   @Override
   public <T> T parseStateless(Class<T> type, ICommandArgument arg) throws CommandInvalidTypeException {
      IArgParser.Stateless<T> parser = this.getParserStateless(type);
      if (parser == null) {
         throw new CommandNoParserForTypeException(type);
      } else {
         try {
            return parser.parseArg(arg);
         } catch (Exception var5) {
            throw new CommandInvalidTypeException(arg, type.getSimpleName());
         }
      }
   }

   @Override
   public <T, S> T parseStated(Class<T> type, Class<S> stateKlass, ICommandArgument arg, S state) throws CommandInvalidTypeException {
      IArgParser.Stated<T, S> parser = this.getParserStated(type, stateKlass);
      if (parser == null) {
         throw new CommandNoParserForTypeException(type);
      } else {
         try {
            return parser.parseArg(arg, state);
         } catch (Exception var7) {
            throw new CommandInvalidTypeException(arg, type.getSimpleName());
         }
      }
   }

   @Override
   public Registry<IArgParser<?>> getRegistry() {
      return this.registry;
   }
}
