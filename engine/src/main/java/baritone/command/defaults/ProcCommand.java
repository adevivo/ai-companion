package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.pathing.calc.IPathingControlManager;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class ProcCommand extends Command {
   public ProcCommand() {
      super("proc");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      IPathingControlManager pathingControlManager = baritone.getPathingControlManager();
      IBaritoneProcess process = pathingControlManager.mostRecentInControl().orElse(null);
      if (process == null) {
         throw new CommandInvalidStateException("No process in control");
      } else {
         this.logDirect(
            source,
            String.format(
               "Class: %s\nPriority: %f\nTemporary: %b\nDisplay name: %s\nLast command: %s",
               process.getClass().getTypeName(),
               process.priority(),
               process.isTemporary(),
               process.displayName(),
               pathingControlManager.mostRecentCommand().map(PathingCommand::toString).orElse("None")
            )
         );
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "View process state information";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The proc command provides miscellaneous information about the process currently controlling an entity.",
         "",
         "You are not expected to understand this if you aren't familiar with implementation details.",
         "",
         "Usage:",
         "> proc - View process information, if present"
      );
   }
}
