package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.BlockById;
import baritone.api.command.exception.CommandException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class FindCommand extends Command {
   public FindCommand() {
      super("find");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      throw new UnsupportedOperationException();
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return args.tabCompleteDatatype(BlockById.INSTANCE);
   }

   @Override
   public String getShortDesc() {
      return "Find positions of a certain block";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList("", "", "Usage:", "> ");
   }
}
