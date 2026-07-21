package baritone.command.manager;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.ICommand;
import baritone.api.command.argument.ICommandArgument;
import baritone.api.command.exception.CommandException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.command.manager.ICommandManager;
import baritone.api.command.registry.Registry;
import baritone.command.CommandUnhandledException;
import baritone.command.argument.ArgConsumer;
import baritone.command.argument.CommandArguments;
import baritone.command.defaults.DefaultCommands;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.Tuple;

public class BaritoneCommandManager implements ICommandManager {
   private final IBaritone baritone;

   public BaritoneCommandManager(Baritone baritone) {
      this.baritone = baritone;
   }

   @Override
   public IBaritone getBaritone() {
      return this.baritone;
   }

   @Override
   public Registry<ICommand> getRegistry() {
      return ICommandManager.registry;
   }

   @Override
   public boolean execute(CommandSourceStack source, String string) throws CommandException {
      return this.execute(source, expand(string));
   }

   @Override
   public boolean execute(CommandSourceStack source, Tuple<String, List<ICommandArgument>> expanded) throws CommandException {
      BaritoneCommandManager.ExecutionWrapper execution = this.from(expanded);
      if (execution != null) {
         execution.execute(source);
      }

      return execution != null;
   }

   @Override
   public Stream<String> tabComplete(Tuple<String, List<ICommandArgument>> expanded) {
      BaritoneCommandManager.ExecutionWrapper execution = this.from(expanded);
      return execution == null ? Stream.empty() : execution.tabComplete();
   }

   @Override
   public Stream<String> tabComplete(String prefix) {
      Tuple<String, List<ICommandArgument>> pair = expand(prefix, true);
      String label = (String)pair.getA();
      List<ICommandArgument> args = (List<ICommandArgument>)pair.getB();
      return args.isEmpty() ? new TabCompleteHelper().addCommands().filterPrefix(label).stream() : this.tabComplete(pair);
   }

   private BaritoneCommandManager.ExecutionWrapper from(Tuple<String, List<ICommandArgument>> expanded) {
      String label = (String)expanded.getA();
      ArgConsumer args = new ArgConsumer(this, (List<ICommandArgument>)expanded.getB(), this.getBaritone());
      ICommand command = ICommandManager.getCommand(label);
      return command == null ? null : new BaritoneCommandManager.ExecutionWrapper(this.baritone, command, label, args);
   }

   private static Tuple<String, List<ICommandArgument>> expand(String string, boolean preserveEmptyLast) {
      String label = string.split("\\s", 2)[0];
      List<ICommandArgument> args = CommandArguments.from(string.substring(label.length()), preserveEmptyLast);
      return new Tuple(label, args);
   }

   public static Tuple<String, List<ICommandArgument>> expand(String string) {
      return expand(string, false);
   }

   static {
      DefaultCommands.controlCommands.registerCommands();
   }

   private static final class ExecutionWrapper {
      private final IBaritone baritone;
      private final ICommand command;
      private final String label;
      private final ArgConsumer args;

      private ExecutionWrapper(IBaritone baritone, ICommand command, String label, ArgConsumer args) {
         this.baritone = baritone;
         this.command = command;
         this.label = label;
         this.args = args;
      }

      private void execute(CommandSourceStack source) throws CommandException {
         try {
            this.command.execute(source, this.label, this.args, this.baritone);
         } catch (Throwable var3) {
            throw var3 instanceof CommandException
               ? (CommandException)var3
               : new CommandUnhandledException(
                  "An unhandled exception occurred. The error is in your game's log, please report this at https://github.com/Ladysnake/Automatone/issues",
                  var3
               );
         }
      }

      private Stream<String> tabComplete() {
         try {
            return this.command.tabComplete(this.label, this.args).map(s -> {
               Deque<ICommandArgument> confirmedArgs = new ArrayDeque<>(this.args.getConsumed());
               confirmedArgs.removeLast();
               return Stream.concat(Stream.of(this.label), confirmedArgs.stream().map(ICommandArgument::getValue)).collect(Collectors.joining(" ")) + " " + s;
            });
         } catch (Throwable var2) {
            return Stream.empty();
         }
      }
   }
}
