package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import java.util.Arrays;

public class ListCommand extends Command {
   public ListCommand() {
      super("list", "List all obtainable items");
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      mod.log("#### LIST OF ALL OBTAINABLE ITEMS ####");
      mod.log(Arrays.toString(TaskCatalogue.resourceNames().toArray()));
      mod.log("############# END LIST ###############");
   }
}
