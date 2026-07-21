package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.util.helpers.StorageHelper;

public class FoodCommand extends Command {
   public FoodCommand() throws CommandException {
      super("food", "Collects a certain amount of food. Example: `food 10` to collect 10 units of food.", new Arg<>(Integer.class, "count"));
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      int foodPoints = parser.get(Integer.class);
      foodPoints += StorageHelper.calculateInventoryFoodScore(mod);
      mod.runUserTask(new CollectFoodTask(foodPoints), () -> this.finish());
   }
}
