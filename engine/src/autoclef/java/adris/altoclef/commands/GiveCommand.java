package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.entity.GiveItemToPlayerTask;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.FuzzySearchHelper;
import adris.altoclef.util.helpers.ItemHelper;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

public class GiveCommand extends Command {
   public GiveCommand() throws CommandException {
      super(
         "give",
         "Give or drop an item to a player. Examples: `give Ellie diamond 3` to give player with username Ellie 3 diamonds.",
         new Arg<>(String.class, "username", null, 2),
         new Arg<>(String.class, "item"),
         new Arg<>(Integer.class, "count", 1, 1)
      );
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      String username = parser.get(String.class);
      if (username == null) {
         if (mod.getOwner() == null) {
            mod.logWarning("No butler user currently present. Running this command with no user argument can ONLY be done via butler.");
            this.finish();
            return;
         }

         username = mod.getOwner().getName().getString();
      }

      String item = parser.get(String.class);
      int count = parser.get(Integer.class);
      ItemTarget target = null;
      if (TaskCatalogue.taskExists(item)) {
         target = TaskCatalogue.getItemTarget(item, count);
      } else {
         for (int i = 0; i < mod.getInventory().getContainerSize(); i++) {
            ItemStack stack = mod.getInventory().getItem(i);
            if (!stack.isEmpty()) {
               String name = ItemHelper.stripItemName(stack.getItem());
               if (name.equals(item)) {
                  target = new ItemTarget(stack.getItem(), count);
                  break;
               }
            }
         }
      }

      if (!mod.getEntityTracker().isPlayerLoaded(username)) {
         String nearbyUsernames = String.join(",", mod.getEntityTracker().getAllLoadedPlayerUsernames());
         Debug.logMessage(
            "No user in render distance found with username \""
               + username
               + "\". Maybe this was a typo or there is a user with a similar name around? Nearby users: ["
               + nearbyUsernames
               + "]."
         );
         this.finish();
      } else {
         if (target != null) {
            Debug.logMessage("USER: " + username + " : ITEM: " + item + " x " + count);
            mod.runUserTask(new GiveItemToPlayerTask(username, target), () -> this.finish());
         } else {
            Set<String> validNames = new HashSet<>(TaskCatalogue.resourceNames());

            for (int ix = 0; ix < mod.getInventory().getContainerSize(); ix++) {
               ItemStack stack = mod.getInventory().getItem(ix);
               if (!stack.isEmpty()) {
                  String name = ItemHelper.stripItemName(stack.getItem());
                  validNames.add(name);
               }
            }

            String closestMatch = FuzzySearchHelper.getClosestMatchMinecraftItems(item, validNames);
            mod.log("Item not found or task does not exist for item: \"" + item + "\". Does the user mean \"" + closestMatch + "\"?");
            this.finish();
         }
      }
   }
}
