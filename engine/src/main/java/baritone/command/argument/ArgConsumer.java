package baritone.command.argument;

import baritone.PlayerEngine;
import baritone.api.IBaritone;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.argument.ICommandArgument;
import baritone.api.command.datatypes.IDatatype;
import baritone.api.command.datatypes.IDatatypeContext;
import baritone.api.command.datatypes.IDatatypeFor;
import baritone.api.command.datatypes.IDatatypePost;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.command.exception.CommandNotEnoughArgumentsException;
import baritone.api.command.exception.CommandTooManyArgumentsException;
import baritone.api.command.manager.ICommandManager;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

public class ArgConsumer implements IArgConsumer, IDatatypeContext {
   private final IBaritone baritone;
   private final ICommandManager manager;
   private final LinkedList<ICommandArgument> args;
   private final Deque<ICommandArgument> consumed;

   private ArgConsumer(ICommandManager manager, Deque<ICommandArgument> args, Deque<ICommandArgument> consumed, IBaritone baritone) {
      this.manager = manager;
      this.args = new LinkedList<>(args);
      this.consumed = new LinkedList<>(consumed);
      this.baritone = baritone;
   }

   public ArgConsumer(ICommandManager manager, List<ICommandArgument> args, IBaritone baritone) {
      this(manager, new LinkedList<>(args), new LinkedList<>(), baritone);
   }

   @Override
   public LinkedList<ICommandArgument> getArgs() {
      return this.args;
   }

   @Override
   public Deque<ICommandArgument> getConsumed() {
      return this.consumed;
   }

   @Override
   public boolean has(int num) {
      return this.args.size() >= num;
   }

   @Override
   public boolean hasAny() {
      return this.has(1);
   }

   @Override
   public boolean hasAtMost(int num) {
      return this.args.size() <= num;
   }

   @Override
   public boolean hasAtMostOne() {
      return this.hasAtMost(1);
   }

   @Override
   public boolean hasExactly(int num) {
      return this.args.size() == num;
   }

   @Override
   public boolean hasExactlyOne() {
      return this.hasExactly(1);
   }

   @Override
   public ICommandArgument peek(int index) throws CommandNotEnoughArgumentsException {
      this.requireMin(index + 1);
      return this.args.get(index);
   }

   @Override
   public ICommandArgument peek() throws CommandNotEnoughArgumentsException {
      return this.peek(0);
   }

   @Override
   public boolean is(Class<?> type, int index) throws CommandNotEnoughArgumentsException {
      return this.peek(index).is(type);
   }

   @Override
   public boolean is(Class<?> type) throws CommandNotEnoughArgumentsException {
      return this.is(type, 0);
   }

   @Override
   public String peekString(int index) throws CommandNotEnoughArgumentsException {
      return this.peek(index).getValue();
   }

   @Override
   public String peekString() throws CommandNotEnoughArgumentsException {
      return this.peekString(0);
   }

   @Override
   public <E extends Enum<?>> E peekEnum(Class<E> enumClass, int index) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException {
      return this.peek(index).getEnum(enumClass);
   }

   @Override
   public <E extends Enum<?>> E peekEnum(Class<E> enumClass) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException {
      return this.peekEnum(enumClass, 0);
   }

   @Override
   public <E extends Enum<?>> E peekEnumOrNull(Class<E> enumClass, int index) throws CommandNotEnoughArgumentsException {
      try {
         return this.peekEnum(enumClass, index);
      } catch (CommandInvalidTypeException var4) {
         return null;
      }
   }

   @Override
   public <E extends Enum<?>> E peekEnumOrNull(Class<E> enumClass) throws CommandNotEnoughArgumentsException {
      return this.peekEnumOrNull(enumClass, 0);
   }

   @Override
   public <T> T peekAs(Class<T> type, int index) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException {
      return this.peek(index).getAs(type);
   }

   @Override
   public <T> T peekAs(Class<T> type) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException {
      return this.peekAs(type, 0);
   }

   @Override
   public <T> T peekAsOrDefault(Class<T> type, T def, int index) throws CommandNotEnoughArgumentsException {
      try {
         return this.peekAs(type, index);
      } catch (CommandInvalidTypeException var5) {
         return def;
      }
   }

   @Override
   public <T> T peekAsOrDefault(Class<T> type, T def) throws CommandNotEnoughArgumentsException {
      return this.peekAsOrDefault(type, def, 0);
   }

   @Override
   public <T> T peekAsOrNull(Class<T> type, int index) throws CommandNotEnoughArgumentsException {
      return this.peekAsOrDefault(type, null, index);
   }

   @Override
   public <T> T peekAsOrNull(Class<T> type) throws CommandNotEnoughArgumentsException {
      return this.peekAsOrNull(type, 0);
   }

   @Override
   public <T> T peekDatatype(IDatatypeFor<T> datatype) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException {
      return this.copy().getDatatypeFor(datatype);
   }

   @Override
   public <T, O> T peekDatatype(IDatatypePost<T, O> datatype) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException {
      return this.peekDatatype(datatype, null);
   }

   @Override
   public <T, O> T peekDatatype(IDatatypePost<T, O> datatype, O original) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException {
      return this.copy().getDatatypePost(datatype, original);
   }

   @Override
   public <T> T peekDatatypeOrNull(IDatatypeFor<T> datatype) {
      return this.copy().getDatatypeForOrNull(datatype);
   }

   @Override
   public <T, O> T peekDatatypeOrNull(IDatatypePost<T, O> datatype) {
      return this.copy().getDatatypePostOrNull(datatype, null);
   }

   @Override
   public <T, O, D extends IDatatypePost<T, O>> T peekDatatypePost(D datatype, O original) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException {
      return this.copy().getDatatypePost(datatype, original);
   }

   @Override
   public <T, O, D extends IDatatypePost<T, O>> T peekDatatypePostOrDefault(D datatype, O original, T def) {
      return this.copy().getDatatypePostOrDefault(datatype, original, def);
   }

   @Override
   public <T, O, D extends IDatatypePost<T, O>> T peekDatatypePostOrNull(D datatype, O original) {
      return this.peekDatatypePostOrDefault(datatype, original, null);
   }

   @Override
   public <T, D extends IDatatypeFor<T>> T peekDatatypeFor(Class<D> datatype) {
      return this.copy().peekDatatypeFor(datatype);
   }

   @Override
   public <T, D extends IDatatypeFor<T>> T peekDatatypeForOrDefault(Class<D> datatype, T def) {
      return this.copy().peekDatatypeForOrDefault(datatype, def);
   }

   @Override
   public <T, D extends IDatatypeFor<T>> T peekDatatypeForOrNull(Class<D> datatype) {
      return this.peekDatatypeForOrDefault(datatype, null);
   }

   @Override
   public ICommandArgument get() throws CommandNotEnoughArgumentsException {
      this.requireMin(1);
      ICommandArgument arg = this.args.removeFirst();
      this.consumed.add(arg);
      return arg;
   }

   @Override
   public String getString() throws CommandNotEnoughArgumentsException {
      return this.get().getValue();
   }

   @Override
   public <E extends Enum<?>> E getEnum(Class<E> enumClass) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException {
      return this.get().getEnum(enumClass);
   }

   @Override
   public <E extends Enum<?>> E getEnumOrDefault(Class<E> enumClass, E def) throws CommandNotEnoughArgumentsException {
      try {
         this.peekEnum(enumClass);
         return this.getEnum(enumClass);
      } catch (CommandInvalidTypeException var4) {
         return def;
      }
   }

   @Override
   public <E extends Enum<?>> E getEnumOrNull(Class<E> enumClass) throws CommandNotEnoughArgumentsException {
      return this.getEnumOrDefault(enumClass, null);
   }

   @Override
   public <T> T getAs(Class<T> type) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException {
      return this.get().getAs(type);
   }

   @Override
   public <T> T getAsOrDefault(Class<T> type, T def) throws CommandNotEnoughArgumentsException {
      try {
         T val = this.peek().getAs(type);
         this.get();
         return val;
      } catch (CommandInvalidTypeException var4) {
         return def;
      }
   }

   @Override
   public <T> T getAsOrNull(Class<T> type) throws CommandNotEnoughArgumentsException {
      return this.getAsOrDefault(type, null);
   }

   @Override
   public <T, O, D extends IDatatypePost<T, O>> T getDatatypePost(D datatype, O original) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException {
      try {
         return datatype.apply(this, original);
      } catch (Exception var4) {
         if (this.baritone.settings().verboseCommandExceptions.get()) {
            PlayerEngine.LOGGER.error(var4);
         }

         throw new CommandInvalidTypeException(this.hasAny() ? this.peek() : this.consumed(), datatype.getClass().getSimpleName(), var4);
      }
   }

   @Override
   public <T, O, D extends IDatatypePost<T, O>> T getDatatypePostOrDefault(D datatype, O original, T _default) {
      List<ICommandArgument> argsSnapshot = new ArrayList<>(this.args);
      List<ICommandArgument> consumedSnapshot = new ArrayList<>(this.consumed);

      try {
         return this.getDatatypePost(datatype, original);
      } catch (Exception var7) {
         this.args.clear();
         this.args.addAll(argsSnapshot);
         this.consumed.clear();
         this.consumed.addAll(consumedSnapshot);
         return _default;
      }
   }

   @Override
   public <T, O, D extends IDatatypePost<T, O>> T getDatatypePostOrNull(D datatype, O original) {
      return this.getDatatypePostOrDefault(datatype, original, null);
   }

   @Override
   public <T, D extends IDatatypeFor<T>> T getDatatypeFor(D datatype) throws CommandInvalidTypeException, CommandNotEnoughArgumentsException {
      try {
         return datatype.get(this);
      } catch (Exception var3) {
         if (this.baritone.settings().verboseCommandExceptions.get()) {
            PlayerEngine.LOGGER.error(var3);
         }

         throw new CommandInvalidTypeException(this.hasAny() ? this.peek() : this.consumed(), datatype.getClass().getSimpleName(), var3);
      }
   }

   @Override
   public <T, D extends IDatatypeFor<T>> T getDatatypeForOrDefault(D datatype, T def) {
      List<ICommandArgument> argsSnapshot = new ArrayList<>(this.args);
      List<ICommandArgument> consumedSnapshot = new ArrayList<>(this.consumed);

      try {
         return this.getDatatypeFor(datatype);
      } catch (Exception var6) {
         this.args.clear();
         this.args.addAll(argsSnapshot);
         this.consumed.clear();
         this.consumed.addAll(consumedSnapshot);
         return def;
      }
   }

   @Override
   public <T, D extends IDatatypeFor<T>> T getDatatypeForOrNull(D datatype) {
      return this.getDatatypeForOrDefault(datatype, null);
   }

   @Override
   public <T extends IDatatype> Stream<String> tabCompleteDatatype(T datatype) {
      try {
         return datatype.tabComplete(this);
      } catch (Exception var3) {
         PlayerEngine.LOGGER.error(var3);
         return Stream.empty();
      }
   }

   @Override
   public String rawRest() {
      return this.args.size() > 0 ? this.args.getFirst().getRawRest() : "";
   }

   @Override
   public void requireMin(int min) throws CommandNotEnoughArgumentsException {
      if (this.args.size() < min) {
         throw new CommandNotEnoughArgumentsException(min + this.consumed.size());
      }
   }

   @Override
   public void requireMax(int max) throws CommandTooManyArgumentsException {
      if (this.args.size() > max) {
         throw new CommandTooManyArgumentsException(max + this.consumed.size());
      }
   }

   @Override
   public void requireExactly(int args) throws CommandException {
      this.requireMin(args);
      this.requireMax(args);
   }

   @Override
   public boolean hasConsumed() {
      return !this.consumed.isEmpty();
   }

   @Override
   public ICommandArgument consumed() {
      return (ICommandArgument)(this.consumed.size() > 0 ? this.consumed.getLast() : CommandArguments.unknown());
   }

   @Override
   public String consumedString() {
      return this.consumed().getValue();
   }

   public ArgConsumer copy() {
      return new ArgConsumer(this.manager, this.args, this.consumed, this.baritone);
   }

   @Override
   public final IBaritone getBaritone() {
      return this.baritone;
   }

   public final ArgConsumer getConsumer() {
      return this;
   }
}
