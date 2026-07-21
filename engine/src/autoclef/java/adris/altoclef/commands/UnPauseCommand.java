package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;

public class UnPauseCommand extends Command {
   public UnPauseCommand() {
      super("unpause", "UnPauses the bot (Still in development!)");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) {
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
