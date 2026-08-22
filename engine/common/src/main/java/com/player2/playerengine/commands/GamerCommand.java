package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.tasks.speedrun.beatgame.BeatMinecraftTask;

public class GamerCommand extends Command {
   public GamerCommand() {
      super("gamer", "Beats the game (Miran version)");
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) {
      mod.runUserTask(new BeatMinecraftTask(mod), () -> this.finish());
   }
}
