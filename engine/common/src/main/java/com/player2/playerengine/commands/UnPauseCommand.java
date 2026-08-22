package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;

public class UnPauseCommand extends Command {
   public UnPauseCommand() {
      super("unpause", "UnPauses the bot (Still in development!)");
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) {
      if (!mod.isPaused()) {
         mod.log("Bot isn't paused");
      } else {
         mod.runUserTask(mod.getStoredTask());
         mod.setPaused(false);
         mod.log("Unpausing Bot and time");
      }

      this.finish();
   }
}
