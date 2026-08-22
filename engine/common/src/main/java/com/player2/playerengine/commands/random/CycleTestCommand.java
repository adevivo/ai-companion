package com.player2.playerengine.commands.random;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.tasks.speedrun.OneCycleTask;

public class CycleTestCommand extends Command {
   public CycleTestCommand() {
      super("cycle", "One cycles the dragon B)");
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) {
      mod.runUserTask(new OneCycleTask(), () -> this.finish());
   }
}
