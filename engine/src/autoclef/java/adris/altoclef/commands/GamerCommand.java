package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.tasks.speedrun.beatgame.BeatMinecraftTask;

public class GamerCommand extends Command {
   public GamerCommand() {
      super("gamer", "Beats the game (Miran version)");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) {
      mod.runUserTask(new BeatMinecraftTask(mod), () -> this.finish());
   }
}
