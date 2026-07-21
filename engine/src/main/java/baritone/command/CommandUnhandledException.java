package baritone.command;

import baritone.PlayerEngine;
import baritone.api.command.exception.CommandException;
import net.minecraft.network.chat.Component;

public class CommandUnhandledException extends CommandException {
   public CommandUnhandledException(String message) {
      super(message);
   }

   public CommandUnhandledException(String message, Throwable cause) {
      super(message, cause);
   }

   @Override
   public Component handle() {
      PlayerEngine.LOGGER.error("An unhandled exception occurred while running a command", this.getCause());
      return super.handle();
   }
}
