package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClefController;
import adris.altoclef.chains.MobDefenseChain;
import adris.altoclef.mixins.LivingEntityMixin;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.PlayerSlot;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;

public abstract class AbstractKillEntityTask extends AbstractDoToEntityTask {
   private static final double OTHER_FORCE_FIELD_RANGE = 2.0;
   private static final double CONSIDER_COMBAT_RANGE = 10.0;

   protected AbstractKillEntityTask() {
      this(10.0, 2.0);
   }

   protected AbstractKillEntityTask(double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
      super(combatGuardLowerRange, combatGuardLowerFieldRadius);
   }

   protected AbstractKillEntityTask(double maintainDistance, double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
      super(maintainDistance, combatGuardLowerRange, combatGuardLowerFieldRadius);
   }

   public static Item bestWeapon(AltoClefController mod) {
      TieredItem toolItem1 = null;
      List<ItemStack> invStacks = mod.getItemStorage().getItemStacksPlayerInventory(true);
      TieredItem toolItem2 = MobDefenseChain.getBestWeapon(mod);
      if (toolItem2 != null) {
         return toolItem2;
      } else {
         Item item = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot(mod.getInventory())).getItem();
         float bestDamage = Float.NEGATIVE_INFINITY;
         if (item instanceof TieredItem handToolItem) {
            bestDamage = handToolItem.getTier().getAttackDamageBonus();
         }

         for (ItemStack invStack : invStacks) {
            if (invStack.getItem() instanceof TieredItem toolItem) {
               float itemDamage = toolItem.getTier().getAttackDamageBonus();
               if (itemDamage > bestDamage) {
                  toolItem1 = toolItem;
                  bestDamage = itemDamage;
               }
            }
         }

         return toolItem1;
      }
   }

   public static boolean equipWeapon(AltoClefController mod) {
      Item bestWeapon = bestWeapon(mod);
      Item equipedWeapon = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot(mod.getInventory())).getItem();
      if (bestWeapon != null && bestWeapon != equipedWeapon) {
         mod.getSlotHandler().forceEquipItem(bestWeapon);
         return true;
      } else {
         return false;
      }
   }

   @Override
   protected Task onEntityInteract(AltoClefController mod, Entity entity) {
      if (!equipWeapon(mod)) {
         float hitProg = this.getAttackCooldownProgress(mod.getPlayer(), 0.0F);
         if (hitProg >= 1.0F && (mod.getPlayer().onGround() || mod.getPlayer().getDeltaMovement().y() < 0.0 || mod.getPlayer().isInWater())) {
            LookHelper.lookAt(mod, entity.getEyePosition());
            mod.getControllerExtras().attack(entity);
         }
      }

      return null;
   }

   /**
    * Ticks between full-strength swings, from the entity's {@code ATTACK_SPEED} attribute — the same
    * formula vanilla uses in {@code Player#getCurrentItemAttackStrengthDelay}, and the same fix
    * already applied in {@link adris.altoclef.control.KillAura#getAttackCooldownProgressPerTick}.
    *
    * <p>This used to return a flat 5.0F: four full-damage swings a second regardless of what was
    * being held, against the 1.6 a diamond sword actually allows. Because {@link
    * #getAttackCooldownProgress} gates on this, the melee kill task was swinging at roughly 2.5x a
    * player's rate with the same weapon — every one of them at full charge, since the gate only
    * passes once the (too-short) cooldown has elapsed.
    *
    * <p>Keeps the old constant as a fallback for entities without the attribute registered, so a
    * mis-registered attribute degrades to the previous behaviour rather than throwing inside a tick.
    */
   public float getAttackCooldownProgressPerTick(LivingEntity entity) {
      if (entity == null || !entity.getAttributes().hasAttribute(Attributes.ATTACK_SPEED)) {
         return 5.0F;
      }

      double attackSpeed = entity.getAttributeValue(Attributes.ATTACK_SPEED);
      return attackSpeed <= 0.0 ? 5.0F : (float)(1.0 / attackSpeed * 20.0);
   }

   public float getAttackCooldownProgress(LivingEntity entity, float baseTime) {
      return Mth.clamp((((LivingEntityMixin)entity).getLastAttackedTicks() + baseTime) / this.getAttackCooldownProgressPerTick(entity), 0.0F, 1.0F);
   }
}
