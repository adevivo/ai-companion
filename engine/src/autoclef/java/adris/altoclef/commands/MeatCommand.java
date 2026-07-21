package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.resources.CollectMeatTask;
import adris.altoclef.util.helpers.StorageHelper;

public class MeatCommand extends Command {
   public MeatCommand() throws CommandException {
      super(
         "meat",
         "Collects a certain amount of food units of meat. ex. `@meat 10` collects 10 units of food (half of the entire hunger bar)",
         new Arg<>(Integer.class, "count")
      );
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      int count = parser.get(Integer.class);
      count += StorageHelper.calculateInventoryFoodScore(mod);
      mod.runUserTask(new CollectMeatTask(count), () -> this.finish());
   }
}
