package baritone.command.manager;

import baritone.api.command.argument.ICommandArgument;
import baritone.api.command.exception.CommandNotEnoughArgumentsException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.command.manager.ICommandManager;
import baritone.command.argument.ArgConsumer;
import baritone.command.argument.CommandArguments;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class BaritoneArgumentType implements ArgumentType<String> {
   public static BaritoneArgumentType baritone() {
      return new BaritoneArgumentType();
   }

   public static String getCommand(CommandContext<?> context, String name) {
      return (String)context.getArgument(name, String.class);
   }

   public String parse(StringReader reader) {
      String text = reader.getRemaining();
      reader.setCursor(reader.getTotalLength());
      return text;
   }

   public Stream<String> tabComplete(ICommandManager manager, String msg) {
      try {
         List<ICommandArgument> args = CommandArguments.from(msg, true);
         ArgConsumer argc = new ArgConsumer(manager, args, manager.getBaritone());
         return argc.hasAtMost(2) && argc.hasExactly(1)
            ? new TabCompleteHelper().addCommands().filterPrefix(argc.getString()).stream()
            : manager.tabComplete(msg);
      } catch (CommandNotEnoughArgumentsException var5) {
         return Stream.empty();
      }
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      return Suggestions.empty();
   }

   public Collection<String> getExamples() {
      return Arrays.asList("goto x y z", "click");
   }
}
