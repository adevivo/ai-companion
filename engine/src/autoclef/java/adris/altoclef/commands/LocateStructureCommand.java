package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.movement.GoToStrongholdPortalTask;
import adris.altoclef.tasks.movement.LocateDesertTempleTask;

public class LocateStructureCommand extends Command {
   public LocateStructureCommand() throws CommandException {
      super(
         "locate_structure",
         "Locate a world generated structure. Only works for stronghold and desert_temple",
         new Arg<>(LocateStructureCommand.Structure.class, "structure")
      );
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      LocateStructureCommand.Structure structure = parser.get(LocateStructureCommand.Structure.class);
      switch (structure) {
         case STRONGHOLD:
            mod.runUserTask(new GoToStrongholdPortalTask(1), () -> this.finish());
            break;
         case DESERT_TEMPLE:
            mod.runUserTask(new LocateDesertTempleTask(), () -> this.finish());
      }
   }

   public static enum Structure {
      DESERT_TEMPLE,
      STRONGHOLD;
   }
}
