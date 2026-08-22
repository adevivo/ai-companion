package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.util.helpers.ItemHelper;
import java.util.HashMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class InventoryCommand extends Command {
   public InventoryCommand() throws CommandException {
      super("inventory", "Prints the bot's inventory OR returns how many of an item the bot has", new Arg<>(String.class, "item", null, 1));
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
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
