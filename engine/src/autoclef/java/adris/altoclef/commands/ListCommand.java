package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import java.util.Arrays;

public class ListCommand extends Command {
   public ListCommand() {
      super("list", "List all obtainable items");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      mod.log("#### LIST OF ALL OBTAINABLE ITEMS ####");
      mod.log(Arrays.toString(TaskCatalogue.resourceNames().toArray()));
      mod.log("############# END LIST ###############");
   }
}
