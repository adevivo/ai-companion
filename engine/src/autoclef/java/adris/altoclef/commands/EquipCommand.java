package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.commandsystem.ItemList;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.misc.HoldItemTask;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;

public class EquipCommand extends Command {
   public EquipCommand() throws CommandException {
      super("equip", "Equips armor to its slot, or holds a tool/weapon (axe, pickaxe, sword) in hand. Examples: `equip iron_chestplate`, `equip wooden_axe`.", new Arg<>(ItemList.class, "[equippable_items]"));
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      ItemTarget[] items;
      if (parser.getArgUnits().length == 1) {
         String var4 = parser.getArgUnits()[0].toLowerCase();
         switch (var4) {
            case "leather":
               items = ItemTarget.of(ItemHelper.LEATHER_ARMORS);
               break;
            case "iron":
               items = ItemTarget.of(ItemHelper.IRON_ARMORS);
               break;
            case "gold":
               items = ItemTarget.of(ItemHelper.GOLDEN_ARMORS);
               break;
            case "diamond":
               items = ItemTarget.of(ItemHelper.DIAMOND_ARMORS);
               break;
            case "netherite":
               items = ItemTarget.of(ItemHelper.NETHERITE_ARMORS);
               break;
            default:
               items = parser.get(ItemList.class).items;
         }
      } else {
         items = parser.get(ItemList.class).items;
      }

      // Partition into armor (goes to armor slots) vs. everything else (held in the main hand).
      // A tool/weapon such as an axe is not an ArmorItem, so it is wielded via HoldItemTask.
      List<ItemTarget> armor = new ArrayList<>();
      List<ItemTarget> hand = new ArrayList<>();
      for (ItemTarget target : items) {
         boolean allArmor = target.getMatches().length > 0;
         for (Item item : target.getMatches()) {
            if (!(item instanceof ArmorItem)) {
               allArmor = false;
               break;
            }
         }
         (allArmor ? armor : hand).add(target);
      }

      // Prefer holding a requested tool/weapon; otherwise equip the requested armor.
      if (!hand.isEmpty()) {
         mod.runUserTask(new HoldItemTask(hand.toArray(new ItemTarget[0])), () -> this.finish());
      } else {
         mod.runUserTask(new EquipArmorTask(armor.toArray(new ItemTarget[0])), () -> this.finish());
      }
   }
}
