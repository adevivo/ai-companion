package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.tasks.movement.IdleTask;

public class IdleCommand extends Command {
   public IdleCommand() {
      super("idle", "Stand still");
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) {
      mod.runUserTask(new IdleTask(), () -> this.finish());
   }
}
