package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.commands.base.ItemList;
import com.player2.playerengine.tasks.container.StoreInStashTask;
import com.player2.playerengine.util.BlockRange;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;

public class StashCommand extends Command {
   public StashCommand() throws CommandException {
      super(
         "stash",
         "Store an item in a chest/container stash. Will deposit ALL non-equipped items if item list is empty.",
         new Arg<>(Integer.class, "x_start"),
         new Arg<>(Integer.class, "y_start"),
         new Arg<>(Integer.class, "z_start"),
         new Arg<>(Integer.class, "x_end"),
         new Arg<>(Integer.class, "y_end"),
         new Arg<>(Integer.class, "z_end"),
         new Arg<>(ItemList.class, "items (empty for ALL)", null, 6, false)
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      BlockPos start = new BlockPos(parser.get(Integer.class), parser.get(Integer.class), parser.get(Integer.class));
      BlockPos end = new BlockPos(parser.get(Integer.class), parser.get(Integer.class), parser.get(Integer.class));
      ItemList itemList = parser.get(ItemList.class);
      ItemTarget[] items;
      if (itemList == null) {
         items = DepositCommand.getAllNonEquippedOrToolItemsAsTarget(mod);
      } else {
         items = itemList.items;
      }

      mod.runUserTask(new StoreInStashTask(true, new BlockRange(start, end, WorldHelper.getCurrentDimension(mod)), items), () -> this.finish());
   }
}
