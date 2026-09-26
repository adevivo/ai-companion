package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.commands.base.ItemList;
import com.player2.playerengine.tasks.container.DepositInContainersTask;
import com.player2.playerengine.tasks.container.WithdrawFromContainersTask;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class DepositCommand extends Command {
   private static final int NEARBY_RANGE = 20;
   private static final Block[] VALID_CONTAINERS = Stream.concat(
         Arrays.stream(new Block[]{Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL}), Arrays.stream(ItemHelper.itemsToBlocks(ItemHelper.SHULKER_BOXES))
      )
      .toArray(Block[]::new);

   public DepositCommand() throws CommandException {
      super(
         "deposit",
         "Put items into the chests, barrels and shulker boxes within 16 blocks, walking to them; places a chest if there is none with room. The count is how many to put in. Pass no arguments to deposit ALL items except tools and armour. Examples: `deposit` deposits ALL items, `deposit diamond 2` deposits 2 diamonds.",
         new Arg<>(ItemList.class, "items (empty for ALL non gear items)", null, 0, false)
      );
   }

   public static ItemTarget[] getAllNonEquippedOrToolItemsAsTarget(PlayerEngineController mod) {
      return StorageHelper.getAllInventoryItemsAsTargets(mod, slot -> {
         if (slot.getInventory().size() == 4) {
            return false;
         } else {
            ItemStack stack = StorageHelper.getItemStackInSlot(slot);
            if (!stack.isEmpty()) {
               Item item = stack.getItem();
               return !(stack.has(DataComponents.TOOL));
            } else {
               return false;
            }
         }
      });
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      ItemList itemList = parser.get(ItemList.class);
      ItemTarget[] items;
      if (itemList == null) {
         items = getAllNonEquippedOrToolItemsAsTarget(mod);
      } else {
         // The count comes from an inventory snapshot the agent saw a round-trip ago, and it keeps
         // farming/mining in the meantime — so asking for more than is held is routine, not an error.
         // Deposit what we actually have rather than refusing the whole thing over a stale number.
         Map<String, Integer> held = new HashMap<>();
         for (int i = 0; i < mod.getInventory().getContainerSize(); i++) {
            ItemStack stack = mod.getInventory().getItem(i);
            if (!stack.isEmpty()) {
               String name = ItemHelper.stripItemName(stack.getItem());
               held.put(name, held.getOrDefault(name, 0) + stack.getCount());
            }
         }

         List<ItemTarget> clamped = new ArrayList<>();
         Map<String, Integer> shortBy = new HashMap<>();
         for (ItemTarget itemTarget : itemList.items) {
            String name = itemTarget.getCatalogueName();
            int available = held.getOrDefault(name, 0);
            int wanted = itemTarget.getTargetCount();
            if (available < wanted) {
               shortBy.put(name, wanted - available);
            }
            int depositable = Math.min(wanted, available);
            if (depositable > 0) {
               clamped.add(new ItemTarget(name, depositable));
               held.put(name, available - depositable);
            }
         }

         if (!shortBy.isEmpty()) {
            String leftover = String.join(", ",
                  shortBy.entrySet().stream().map(e -> e.getValue() + " " + e.getKey()).toList());
            mod.logAgentNotice("Asked to deposit more than we are carrying (short by " + leftover
                  + ") — depositing what we have.");
         }

         if (clamped.isEmpty()) {
            mod.logAgentNotice("Nothing to deposit — none of the requested items are in our inventory.");
            this.finish();
            return;
         }
         items = clamped.toArray(new ItemTarget[0]);
      }

      DepositInContainersTask task = new DepositInContainersTask(items);
      mod.runUserTask(task, () -> {
         WithdrawFromContainersTask.Outcome outcome = task.outcome();
         if (outcome.success()) {
            mod.reportCommandResult(outcome.message());
         } else {
            mod.logAgentNotice(outcome.message(), "I couldn't store everything — the chests nearby are full.");
         }
         this.finish();
      });
   }
}
