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
   private static final int DEFAULT_RANGE = 16;

   public FarmCommand() throws CommandException {
      super(
         "farm",
         "Harvests and replants EXISTING nearby crops within range. This does NOT create a farm — it only tends"
            + " one that is already planted. To build a new field, use build_structure instead."
            + " Range is optional and defaults to 16. Example: `farm 10` to tend crops within 10 blocks",
         // Optional: models routinely emit a bare `farm`, which used to hard-fail on a missing argument and
         // burn a whole LLM round-trip recovering. minArgCountToUseDefault=0 => applies when nothing is given.
         new Arg<>(Integer.class, "range", DEFAULT_RANGE, 0)
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
