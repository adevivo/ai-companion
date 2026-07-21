package adris.altoclef.util.helpers;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.multiversion.item.ItemVer;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.Pair;
import adris.altoclef.util.RecipeTarget;
import adris.altoclef.util.slots.Slot;
import baritone.api.entity.IInventoryProvider;
import baritone.api.entity.LivingEntityInventory;
import baritone.utils.ToolSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class StorageHelper {
   public static ItemStack getItemStackInSlot(Slot slot) {
      return slot != null && !slot.equals(Slot.UNDEFINED) ? slot.getStack() : ItemStack.EMPTY;
   }

   public static ItemStack getItemStackInCursorSlot(AltoClefController controller) {
      return controller.getSlotHandler().getCursorStack();
   }

   public static boolean isArmorEquipped(AltoClefController controller, Item... any) {
      for (Item item : any) {
         if (item instanceof ArmorItem armor) {
            ItemStack equippedStack = controller.getEntity().getItemBySlot(armor.getType().getSlot());
            if (equippedStack.is(item)) {
               return true;
            }
         } else if (item instanceof ShieldItem) {
            ItemStack equippedStack = controller.getEntity().getItemBySlot(EquipmentSlot.OFFHAND);
            if (equippedStack.is(item)) {
               return true;
            }
         }
      }

      return false;
   }

   public static boolean isArmorEquippedAll(AltoClefController controller, Item... all) {
      return Arrays.stream(all).allMatch(item -> isArmorEquipped(controller, item));
   }

   public static boolean isItemInOffhand(AltoClefController controller, Item item) {
      return controller.getEntity().getItemBySlot(EquipmentSlot.OFFHAND).is(item);
   }

   public static boolean isEquipped(AltoClefController controller, Item... items) {
      LivingEntityInventory inv = ((IInventoryProvider)controller.getEntity()).getLivingInventory();
      return Arrays.stream(items).anyMatch(item -> inv.getMainHandStack().is(item));
   }

   public static MiningRequirement getCurrentMiningRequirement(AltoClefController controller) {
      MiningRequirement[] order = new MiningRequirement[]{MiningRequirement.DIAMOND, MiningRequirement.IRON, MiningRequirement.STONE, MiningRequirement.WOOD};

      for (MiningRequirement check : order) {
         if (miningRequirementMet(controller, check)) {
            return check;
         }
      }

      return MiningRequirement.HAND;
   }

   private static boolean h(AltoClefController controller, boolean inventoryOnly, Item... items) {
      return inventoryOnly ? controller.getItemStorage().hasItemInventoryOnly(items) : controller.getItemStorage().hasItem(items);
   }

   private static boolean miningRequirementMetInner(AltoClefController controller, boolean inventoryOnly, MiningRequirement requirement) {
      return switch (requirement) {
         case HAND -> true;
         case WOOD -> h(controller, inventoryOnly, Items.WOODEN_PICKAXE)
            || h(controller, inventoryOnly, Items.STONE_PICKAXE)
            || h(controller, inventoryOnly, Items.IRON_PICKAXE)
            || h(controller, inventoryOnly, Items.GOLDEN_PICKAXE)
            || h(controller, inventoryOnly, Items.DIAMOND_PICKAXE)
            || h(controller, inventoryOnly, Items.NETHERITE_PICKAXE);
         case STONE -> h(controller, inventoryOnly, Items.STONE_PICKAXE)
            || h(controller, inventoryOnly, Items.IRON_PICKAXE)
            || h(controller, inventoryOnly, Items.GOLDEN_PICKAXE)
            || h(controller, inventoryOnly, Items.DIAMOND_PICKAXE)
            || h(controller, inventoryOnly, Items.NETHERITE_PICKAXE);
         case IRON -> h(controller, inventoryOnly, Items.IRON_PICKAXE)
            || h(controller, inventoryOnly, Items.GOLDEN_PICKAXE)
            || h(controller, inventoryOnly, Items.DIAMOND_PICKAXE)
            || h(controller, inventoryOnly, Items.NETHERITE_PICKAXE);
         case DIAMOND -> h(controller, inventoryOnly, Items.DIAMOND_PICKAXE) || h(controller, inventoryOnly, Items.NETHERITE_PICKAXE);
         default -> {
            Debug.logError("You missed a spot");
            yield false;
         }
      };
   }

   public static boolean miningRequirementMet(AltoClefController controller, MiningRequirement requirement) {
      return miningRequirementMetInner(controller, false, requirement);
   }

   public static boolean miningRequirementMetInventory(AltoClefController controller, MiningRequirement requirement) {
      return miningRequirementMetInner(controller, true, requirement);
   }

   public static Optional<Slot> getBestToolSlot(AltoClefController controller, BlockState state) {
      int bestSlot = new ToolSet(controller.getPlayer()).getBestSlot(state.getBlock(), false);
      return Optional.of(new Slot(controller.getInventory().main, bestSlot));
   }

   public static boolean shouldSaveStack(AltoClefController controller, Block block, ItemStack stack) {
      return false;
   }

   public static Optional<Slot> getGarbageSlot(AltoClefController controller) {
      LivingEntityInventory inventory = ((IInventoryProvider)controller.getEntity()).getLivingInventory();
      int bestScore = Integer.MIN_VALUE;
      Slot bestSlot = null;

      for (int i = 0; i < inventory.main.size(); i++) {
         ItemStack stack = inventory.getItem(i);
         if (!stack.isEmpty() && ItemHelper.canThrowAwayStack(controller, stack)) {
            int score = 0;
            if (controller.getModSettings().isThrowaway(stack.getItem())) {
               score += 1000;
            }

            if (stack.getCount() < stack.getMaxStackSize()) {
               score += 100;
            }

            score -= stack.getCount();
            if (score > bestScore) {
               bestScore = score;
               bestSlot = new Slot(inventory.main, i);
            }
         }
      }

      return Optional.ofNullable(bestSlot);
   }

   public static int getBuildingMaterialCount(AltoClefController controller) {
      return controller.getItemStorage()
         .getItemCount(
            Arrays.stream(controller.getModSettings().getThrowawayItems(controller, true))
               .filter(item -> item instanceof BlockItem && !item.equals(Items.GRAVEL) && !item.equals(Items.SAND))
               .toArray(Item[]::new)
         );
   }

   public static boolean itemTargetsMet(AltoClefController controller, ItemTarget... targetsToMeet) {
      return Arrays.stream(targetsToMeet).allMatch(target -> controller.getItemStorage().getItemCount(target.getMatches()) >= target.getTargetCount());
   }

   public static boolean itemTargetsMetInventory(AltoClefController controller, ItemTarget... targetsToMeet) {
      return Arrays.stream(targetsToMeet)
         .allMatch(target -> controller.getItemStorage().getItemCountInventoryOnly(target.getMatches()) >= target.getTargetCount());
   }

   public static boolean hasRecipeMaterialsOrTarget(AltoClefController controller, RecipeTarget... targets) {
      HashMap<Item, Pair<ItemTarget, Integer>> required = new HashMap<>();

      for (RecipeTarget target : targets) {
         int craftsNeeded = (int)Math.ceil(
            (double)(target.getTargetCount() - controller.getItemStorage().getItemCount(target.getOutputItem())) / target.getRecipe().outputCount()
         );
         if (craftsNeeded > 0) {
            for (ItemTarget ingredient : target.getRecipe().getSlots()) {
               if (ingredient != null && !ingredient.isEmpty()) {
                  Item item = ingredient.getMatches()[0];
                  required.put(
                     item,
                     new Pair<>(ingredient, required.getOrDefault(item, new Pair<>(ingredient, 0)).getRight() + ingredient.getTargetCount() * craftsNeeded)
                  );
               }
            }
         }
      }

      for (Item item : required.keySet()) {
         Pair<ItemTarget, Integer> req = required.get(item);
         if (controller.getItemStorage().getItemCount(req.getLeft()) < req.getRight()) {
            return false;
         }
      }

      return true;
   }

   public static int calculateInventoryFoodScore(AltoClefController controller) {
      int result = 0;

      for (ItemStack stack : controller.getItemStorage().getItemStacksPlayerInventory(true)) {
         if (ItemVer.isFood(stack)) {
            result += Objects.requireNonNull(ItemVer.getFoodComponent(stack.getItem())).getHunger() * stack.getCount();
         }
      }

      return result;
   }

   public static double calculateInventoryFuelCount(AltoClefController controller) {
      double result = 0.0;

      for (ItemStack stack : controller.getItemStorage().getItemStacksPlayerInventory(true)) {
         if (controller.getModSettings().isSupportedFuel(stack.getItem())) {
            result += ItemHelper.getFuelAmount(stack.getItem()) * stack.getCount();
         }
      }

      return result;
   }

   public static ItemTarget[] getAllInventoryItemsAsTargets(AltoClefController controller, Predicate<Slot> accept) {
      HashMap<Item, Integer> counts = new HashMap<>();
      LivingEntityInventory inv = ((IInventoryProvider)controller.getEntity()).getLivingInventory();

      for (int i = 0; i < inv.main.size(); i++) {
         Slot slot = new Slot(inv.main, i);
         if (accept.test(slot)) {
            ItemStack stack = getItemStackInSlot(slot);
            if (!stack.isEmpty()) {
               counts.put(stack.getItem(), counts.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
         }
      }

      Slot offhandSlot = new Slot(inv.offHand, 0);
      if (accept.test(offhandSlot)) {
         ItemStack stack = getItemStackInSlot(offhandSlot);
         if (!stack.isEmpty()) {
            counts.put(stack.getItem(), counts.getOrDefault(stack.getItem(), 0) + stack.getCount());
         }
      }

      for (int ix = 0; ix < inv.armor.size(); ix++) {
         Slot slot = new Slot(inv.armor, ix);
         if (accept.test(slot)) {
            ItemStack stack = getItemStackInSlot(slot);
            if (!stack.isEmpty()) {
               counts.put(stack.getItem(), counts.getOrDefault(stack.getItem(), 0) + stack.getCount());
            }
         }
      }

      ItemTarget[] results = new ItemTarget[counts.size()];
      int ixx = 0;

      for (Item item : counts.keySet()) {
         results[ixx++] = new ItemTarget(item, counts.get(item));
      }

      return results;
   }

   public static int getNumberOfThrowawayBlocks(AltoClefController mod) {
      int totalBlockThrowaways = 0;
      if (!mod.getItemStorage().getSlotsWithItemPlayerInventory(false, mod.getModSettings().getThrowawayItems(mod)).isEmpty()) {
         for (Slot slot : mod.getItemStorage().getSlotsWithItemPlayerInventory(false, mod.getModSettings().getThrowawayItems(mod))) {
            ItemStack stack = getItemStackInSlot(slot);
            if (ItemHelper.canThrowAwayStack(mod, stack) && stack.getItem() instanceof BlockItem) {
               totalBlockThrowaways += stack.getCount();
            }
         }
      }

      return totalBlockThrowaways;
   }

   public static Optional<Slot> getSlotWithThrowawayBlock(AltoClefController mod, boolean limitToHotbar) {
      List<Slot> throwawayBlockItems = new ArrayList<>();
      int totalBlockThrowaways = 0;
      if (!mod.getItemStorage().getSlotsWithItemPlayerInventory(false, mod.getModSettings().getThrowawayItems(mod)).isEmpty()) {
         for (Slot slot : mod.getItemStorage().getSlotsWithItemPlayerInventory(false, mod.getModSettings().getThrowawayItems(mod))) {
            if (!Slot.isCursor(slot) && (!limitToHotbar || slot.getInventorySlot() <= 8 && slot.getInventorySlot() >= 0)) {
               ItemStack stack = getItemStackInSlot(slot);
               if (ItemHelper.canThrowAwayStack(mod, stack) && stack.getItem() instanceof BlockItem) {
                  totalBlockThrowaways += stack.getCount();
                  throwawayBlockItems.add(slot);
               }
            }
         }
      }

      if (!throwawayBlockItems.isEmpty()) {
         Iterator var7 = throwawayBlockItems.iterator();
         if (var7.hasNext()) {
            Slot throwawayBlockItem = (Slot)var7.next();
            return Optional.ofNullable(throwawayBlockItem);
         }
      }

      return Optional.empty();
   }
}
