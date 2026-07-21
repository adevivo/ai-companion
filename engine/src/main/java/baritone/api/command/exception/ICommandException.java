package baritone.api.command.exception;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public interface ICommandException {
   String getMessage();

   default Component handle() {
      return Component.literal(this.getMessage()).withStyle(ChatFormatting.RED);
   }
}
