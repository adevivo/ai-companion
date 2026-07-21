package baritone.command.defaults;

import baritone.PlayerEngine;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.utils.accessor.ServerCommandSourceAccessor;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;

public class ClickCommand extends Command {
   public static final ResourceLocation OPEN_CLICK_SCREEN = PlayerEngine.id("open_click_screen");

   public ClickCommand() {
      super("click");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);

      try {
         CommandSource t = ((ServerCommandSourceAccessor)source).automatone$getOutput();
      } catch (Throwable var6) {
         PlayerEngine.LOGGER.error("Failed to open click screen, is this a dedicated server?", var6);
      }

      this.logDirect(source, "aight dude");
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Open click";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList("Opens click dude", "", "Usage:", "> click");
   }
}
