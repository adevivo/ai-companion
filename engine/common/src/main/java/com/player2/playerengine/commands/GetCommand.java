package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.commands.base.ItemList;
import com.player2.playerengine.player2api.AgentCommandUtils;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;

public class GetCommand extends Command {
   public GetCommand() throws CommandException {
      super(
         "get",
         "Get a resource or Craft an item in Minecraft. You can craft item even if you don't have ingredients in inventory already. Examples: `get log 20` gets 20 logs, `get diamond_chestplate 1` gets 1 diamond chestplate. For equipments you have to specify the type of equipments like wooden, stone, iron, golden and diamond.",
         new Arg<>(ItemList.class, "items")
      );
   }

   private void getItems(PlayerEngineController mod, ItemTarget... items) {
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
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      ItemList items = parser.get(ItemList.class);
      this.getItems(mod, items.items);
   }
}
