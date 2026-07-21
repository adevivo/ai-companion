package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.commandsystem.GotoTarget;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.GetToXZTask;
import adris.altoclef.tasks.movement.GetToYTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

public class GotoCommand extends Command {
   public GotoCommand() throws CommandException {
      super(
         "goto",
         "Tell bot to travel to a set of coordinates",
         new Arg<>(GotoTarget.class, "[x y z dimension]/[x z dimension]/[y dimension]/[dimension]/[x y z]/[x z]/[y]")
      );
   }

   public static Task getMovementTaskFor(GotoTarget target) {
      return (Task)(switch (target.getType()) {
         case XYZ -> new GetToBlockTask(new BlockPos(target.getX(), target.getY(), target.getZ()), target.getDimension());
         case XZ -> new GetToXZTask(target.getX(), target.getZ(), target.getDimension());
         case Y -> new GetToYTask(target.getY(), target.getDimension());
         case NONE -> new DefaultGoToDimensionTask(target.getDimension());
      });
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      GotoTarget target = parser.get(GotoTarget.class);
      mod.runUserTask(getMovementTaskFor(target), () -> this.finish());
   }
}
