package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.misc.FishTask;

public class FishCommand extends Command {
   public FishCommand() throws CommandException {
      super("fish", "Starts fishing automatically.  Example: `fish` to start fishing. NEEDS FISHING ROD");
   }

   @Override
   protected void call(AltoClefController controller, ArgParser parser) throws CommandException {
      controller.runUserTask(new FishTask(), () -> this.finish());
   }
}
