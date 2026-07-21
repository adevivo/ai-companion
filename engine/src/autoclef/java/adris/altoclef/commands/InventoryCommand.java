package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.util.helpers.ItemHelper;
import java.util.HashMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class InventoryCommand extends Command {
   public InventoryCommand() throws CommandException {
      super("inventory", "Prints the bot's inventory OR returns how many of an item the bot has", new Arg<>(String.class, "item", null, 1));
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      String item = parser.get(String.class);
      if (item == null) {
         HashMap<String, Integer> counts = new HashMap<>();

         for (int i = 0; i < mod.getInventory().getContainerSize(); i++) {
            ItemStack stack = mod.getInventory().getItem(i);
            if (!stack.isEmpty()) {
               String name = ItemHelper.stripItemName(stack.getItem());
               if (!counts.containsKey(name)) {
                  counts.put(name, 0);
               }

               counts.put(name, counts.get(name) + stack.getCount());
            }
         }

         mod.log("INVENTORY: ");

         for (String name : counts.keySet()) {
            mod.log(name + " : " + counts.get(name));
         }

         mod.log("(inventory list sent) ");
      } else {
         Item[] matches = TaskCatalogue.getItemMatches(item);
         if (matches == null || matches.length == 0) {
            mod.logWarning("Item \"" + item + "\" is not catalogued/recognized.");
            this.finish();
            return;
         }

         int count = mod.getItemStorage().getItemCount(matches);
         if (count == 0) {
            mod.log(item + " COUNT: (none)");
         } else {
            mod.log(item + " COUNT: " + count);
         }
      }

      this.finish();
   }
}
