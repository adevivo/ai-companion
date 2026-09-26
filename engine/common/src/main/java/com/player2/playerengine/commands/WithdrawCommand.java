package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.container.WithdrawFromContainersTask;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.FuzzySearchHelper;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/** `withdraw`: take items out of nearby chests, barrels and shulker boxes. */
public class WithdrawCommand extends Command {
   public WithdrawCommand() throws CommandException {
      super(
         "withdraw",
         "Take items out of the chests, barrels and shulker boxes within 16 blocks, walking to each. The count is how many to take; leave it out to take all of them. Examples: `withdraw white_wool 3`, `withdraw wool 3` (any colour), `withdraw oak_log`.",
         new Arg<>(String.class, "item"),
         new Arg<>(Integer.class, "count", WithdrawFromContainersTask.ALL, 1, false)
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      String name = parser.get(String.class).toLowerCase().trim();
      int count = parser.get(Integer.class);
      if (count == 0 || count < WithdrawFromContainersTask.ALL) {
         throw new CommandException("Count must be at least 1, or left out to take all of them.");
      }

      WithdrawFromContainersTask task = new WithdrawFromContainersTask(resolve(name), name, count);
      mod.runUserTask(task, () -> {
         WithdrawFromContainersTask.Outcome outcome = task.outcome();
         if (outcome.success()) {
            mod.reportCommandResult(outcome.message());
         } else {
            mod.logAgentNotice(outcome.message(), "I couldn't get all the " + name + " from the chests.");
         }
         this.finish();
      });
   }

   /**
    * A catalogue name ("wool", "log") matches every variant; anything else is looked up as a plain item
    * id, so an item nobody has written a gathering recipe for can still be taken out of a chest.
    */
   private static ItemTarget resolve(String name) throws CommandException {
      if (TaskCatalogue.taskExists(name)) {
         return TaskCatalogue.getItemTarget(name, 1);
      }
      Optional<Item> item = Optional.ofNullable(Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name))
         .flatMap(BuiltInRegistries.ITEM::getOptional);
      if (item.isPresent()) {
         return new ItemTarget(item.get(), 1);
      }
      String closest = FuzzySearchHelper.getClosestMatchMinecraftItems(name, TaskCatalogue.resourceNames());
      throw new CommandException("Unknown item \"" + name + "\"." + (closest == null ? "" : " Did you mean \"" + closest + "\"?"));
   }
}
