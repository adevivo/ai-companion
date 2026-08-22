package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;

public class PauseCommand extends Command {
   public PauseCommand() {
      super("pause", "Pauses the bot after the task thats running (Still in development!)");
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) {
      mod.setStoredTask(mod.getUserTaskChain().getCurrentTask());
      mod.setPaused(true);
      mod.getUserTaskChain().stop();
      mod.log("Pausing Bot and time");
      this.finish();
   }
}
