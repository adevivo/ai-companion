package baritone.api.command.exception;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class CommandNotFoundException extends CommandException {
   public final String command;

   public CommandNotFoundException(String command) {
      super(String.format("Command not found: %s", command));
      this.command = command;
   }

   @Override
   public Component handle() {
      return Component.literal(this.getMessage()).withStyle(ChatFormatting.GRAY);
   }
}
