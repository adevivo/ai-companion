package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;

public class PauseCommand extends Command {
   public PauseCommand() {
      super("pause", "Pauses the bot after the task thats running (Still in development!)");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) {
      mod.setStoredTask(mod.getUserTaskChain().getCurrentTask());
      mod.setPaused(true);
      mod.getUserTaskChain().stop();
      mod.log("Pausing Bot and time");
      this.finish();
   }
}
