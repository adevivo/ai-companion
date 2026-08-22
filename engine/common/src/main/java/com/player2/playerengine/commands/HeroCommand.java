package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.entity.HeroTask;

public class HeroCommand extends Command {
   public HeroCommand() {
      super("hero", "Kill all hostile mobs");
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      mod.runUserTask(new HeroTask(), () -> this.finish());
   }
}
