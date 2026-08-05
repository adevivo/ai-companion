package adris.altoclef.chains;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.BehaviorConfig;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.ItemTarget;
import baritone.api.entity.LivingEntityInventory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Wear the best armour in the pack, without being asked.
 *
 * <p>Nothing did this. {@code EquipArmorTask} exists but is only ever reached through the {@code equip}
 * command or the speedrun tasks, so a companion handed a full set of diamond would carry it around and
 * keep fighting in its shirt. Handing armour over and then having to dress the companion yourself is a
 * strange thing to have to do, and it is silent — there is no message saying the armour was ignored.
 *
 * <p>Runs on a timer rather than every tick, since scanning the inventory to answer a question whose
 * answer almost never changes is not worth doing twenty times a second.
 *
 * <p>Never owns the task slot: it returns {@code NEGATIVE_INFINITY} and does its work in
 * {@link #getPriority}, the same shape as {@code PreEquipItemChain}. Swapping an armour slot does not
 * interrupt walking, fighting or eating, so there is nothing to schedule around.
 */
public class AutoEquipArmorChain extends SingleTaskChain {

   /** The four slots worth managing. The offhand belongs to the shield logic in {@code MobDefenseChain}. */
   private static final EquipmentSlot[] ARMOR_SLOTS = {
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };

   private static final long CHECK_INTERVAL_MS = 1000L;

   private long lastCheckMs;

   public AutoEquipArmorChain(TaskRunner runner) {
      super(runner);
   }

   @Override
   protected void onTaskFinish(AltoClefController mod) {
   }

   @Override
   public boolean isActive() {
      return true;
   }

   @Override
   public String getName() {
      return "Auto-equip armor";
   }

   @Override
   public float getPriority() {
      if (this.controller == null || !BehaviorConfig.autoEquipArmor) {
         return Float.NEGATIVE_INFINITY;
      }
      long now = System.currentTimeMillis();
      if (now - this.lastCheckMs < CHECK_INTERVAL_MS) {
         return Float.NEGATIVE_INFINITY;
      }
      this.lastCheckMs = now;
      this.equipBestArmor(this.controller);
      return Float.NEGATIVE_INFINITY;
   }

   /** Put on anything in the pack that beats what is currently worn, slot by slot. */
   private void equipBestArmor(AltoClefController mod) {
      LivingEntity self = mod.getEntity();
      LivingEntityInventory inventory = mod.getInventory();
      if (self == null || inventory == null) {
         return;
      }

      for (EquipmentSlot slot : ARMOR_SLOTS) {
         ItemStack worn = self.getItemBySlot(slot);
         int bestScore = score(worn);
         ArmorItem best = null;

         for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armor)) {
               continue;
            }
            if (armor.getType().getSlot() != slot) {
               continue;
            }
            int candidate = score(stack);
            if (candidate > bestScore) {
               bestScore = candidate;
               best = armor;
            }
         }

         if (best != null) {
            // forceEquipArmor swaps the worn piece back into the inventory slot it came from, so an
            // upgrade never costs the old piece.
            mod.getSlotHandler().forceEquipArmor(mod, new ItemTarget(best));
            mod.log("Put on " + best.getDescription().getString() + ".");
         }
      }
   }

   /**
    * How good a piece of armour is, for comparison only.
    *
    * <p>Defence dominates, toughness breaks ties between materials that protect equally, and
    * Protection is worth counting because an enchanted iron chestplate genuinely can beat a bare
    * diamond one. The weights are not a damage model and are not meant to be — they only have to order
    * the handful of pieces a companion is realistically carrying.
    *
    * <p>Durability is deliberately ignored. A nearly-broken diamond helmet still protects exactly as
    * well as a fresh one right up until it breaks, and preferring intact-but-worse armour would be
    * wrong every tick before that moment.
    */
   private static int score(ItemStack stack) {
      if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armor)) {
         return 0;
      }
      int protection = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION, stack);
      return armor.getDefense() * 10 + Math.round(armor.getToughness() * 2.0F) + protection;
   }
}
