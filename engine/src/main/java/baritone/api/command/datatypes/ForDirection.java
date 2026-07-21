package baritone.api.command.datatypes;

import baritone.api.command.exception.CommandException;
import baritone.api.command.helpers.TabCompleteHelper;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.core.Direction;

public enum ForDirection implements IDatatypeFor<Direction> {
   INSTANCE;

   public Direction get(IDatatypeContext ctx) throws CommandException {
      return Direction.valueOf(ctx.getConsumer().getString().toUpperCase(Locale.US));
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
      return new TabCompleteHelper()
         .append(Stream.of(Direction.values()).<String>map(Direction::getName).map(String::toLowerCase))
         .filterPrefix(ctx.getConsumer().getString())
         .stream();
   }
}
