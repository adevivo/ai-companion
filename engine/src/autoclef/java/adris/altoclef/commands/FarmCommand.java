package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.misc.FarmTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

public class FarmCommand extends Command {
   public FarmCommand() throws CommandException {
      super(
         "farm",
         "Starts farming nearby crops automatically within range.  Example: `farm 10` to farm crops withing a range of 10 blocks",
         new Arg<>(Integer.class, "range")
      );
   }

   @Override
   protected void call(AltoClefController controller, ArgParser parser) throws CommandException {
      Integer range = parser.get(Integer.class);
      BlockPos origin = controller.getEntity().blockPosition();
      Task farmTask = new FarmTask(range, origin);
      controller.runUserTask(farmTask, () -> this.finish());
   }
}
