package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.resources.CollectFoodTask;
import com.player2.playerengine.util.helpers.StorageHelper;

public class FoodCommand extends Command {
   public FoodCommand() throws CommandException {
      super(
         "food",
         "GATHERS food by foraging and hunting — this does NOT eat anything and may take you far from"
            + " where you are. To eat food you are already carrying, use `eat` instead."
            + " Example: `food 10` to go and collect 10 units of food.",
         new Arg<>(Integer.class, "count")
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      int foodPoints = parser.get(Integer.class);
      foodPoints += StorageHelper.calculateInventoryFoodScore(mod);
      mod.runUserTask(new CollectFoodTask(foodPoints), () -> this.finish());
   }
}
