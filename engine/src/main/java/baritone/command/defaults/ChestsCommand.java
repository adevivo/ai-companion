package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.cache.IRememberedInventory;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.utils.BetterBlockPos;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

public class ChestsCommand extends Command {
   public ChestsCommand() {
      super("chests");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      Set<Entry<BlockPos, IRememberedInventory>> entries = baritone.getEntityContext().worldData().getContainerMemory().getRememberedInventories().entrySet();
      if (entries.isEmpty()) {
         throw new CommandInvalidStateException("No remembered inventories");
      } else {
         for (Entry<BlockPos, IRememberedInventory> entry : entries) {
            BetterBlockPos pos = new BetterBlockPos(entry.getKey());
            IRememberedInventory inv = entry.getValue();
            this.logDirect(source, pos.toString());

            for (ItemStack item : inv.getContents()) {
               MutableComponent component = (MutableComponent)item.getHoverName();
               component.append(String.format(" x %d", item.getCount()));
               this.logDirect(source, new Component[]{component});
            }
         }
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Display remembered inventories";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList("The chests command lists remembered inventories, I guess?", "", "Usage:", "> chests");
   }
}
