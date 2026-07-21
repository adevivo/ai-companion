package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class VersionCommand extends Command {
   public VersionCommand() {
      super("version");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      this.logDirect(source, "You are running Automatone integrated into PlayerEngine");
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "View Automatone's version";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The version command prints the version of Automatone you're currently running.", "", "Usage:", "> version - View version information, if present"
      );
   }
}
