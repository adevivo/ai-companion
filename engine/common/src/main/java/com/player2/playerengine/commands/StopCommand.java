package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;

public class StopCommand extends Command {
   public StopCommand() {
      super("stop", "Stop task runner (stops all automation), also stops the IDLE task until a new task is started");
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) {
      mod.stop();
      this.finish();
   }
}
