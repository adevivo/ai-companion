package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.misc.FishTask;

public class FishCommand extends Command {
   public FishCommand() throws CommandException {
      super("fish", "Starts fishing automatically.  Example: `fish` to start fishing. NEEDS FISHING ROD");
   }

   @Override
   protected void call(PlayerEngineController controller, ArgParser parser) throws CommandException {
      controller.runUserTask(new FishTask(), () -> this.finish());
   }
}
