package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.utils.BetterBlockPos;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;

public class RenderCommand extends Command {
   public RenderCommand() {
      super("render");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      Minecraft mc = Minecraft.getInstance();
      mc.execute(() -> {
         BetterBlockPos origin = baritone.getEntityContext().feetPos();
         int renderDistance = ((Integer)mc.options.renderDistance().get() + 1) * 16;
         mc.levelRenderer.setBlocksDirty(origin.x - renderDistance, 0, origin.z - renderDistance, origin.x + renderDistance, 255, origin.z + renderDistance);
         this.logDirect(source, "Done");
      });
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Fix glitched chunks";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList("The render command fixes glitched chunk rendering without having to reload all of them.", "", "Usage:", "> render");
   }
}
