package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.resources.CollectMeatTask;
import com.player2.playerengine.util.helpers.StorageHelper;

public class MeatCommand extends Command {
   public MeatCommand() throws CommandException {
      super(
         "meat",
         "Collects a certain amount of food units of meat. ex. `@meat 10` collects 10 units of food (half of the entire hunger bar)",
         new Arg<>(Integer.class, "count")
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      int count = parser.get(Integer.class);
      count += StorageHelper.calculateInventoryFoodScore(mod);
      mod.runUserTask(new CollectMeatTask(count), () -> this.finish());
   }
}
