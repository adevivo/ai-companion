package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.commandsystem.ItemList;
import adris.altoclef.player2api.AgentCommandUtils;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;

public class GetCommand extends Command {
   public GetCommand() throws CommandException {
      super(
         "get",
         "Get a resource or Craft an item in Minecraft. You can craft item even if you don't have ingredients in inventory already. Examples: `get log 20` gets 20 logs, `get diamond_chestplate 1` gets 1 diamond chestplate. For equipments you have to specify the type of equipments like wooden, stone, iron, golden and diamond.",
         new Arg<>(ItemList.class, "items")
      );
   }

   private void getItems(AltoClefController mod, ItemTarget... items) {
      items = AgentCommandUtils.addPresentItemsToTargets(mod, items);
      if (items != null && items.length != 0) {
         Task targetTask;
         if (items.length == 1) {
            targetTask = TaskCatalogue.getItemTask(items[0]);
         } else {
            targetTask = TaskCatalogue.getSquashedItemTask(items);
         }

         if (targetTask != null) {
            mod.runUserTask(targetTask, () -> this.finish());
         } else {
            this.finish();
         }
      } else {
         mod.log("You must specify at least one item!");
         this.finish();
      }
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      ItemList items = parser.get(ItemList.class);
      this.getItems(mod, items.items);
   }
}
