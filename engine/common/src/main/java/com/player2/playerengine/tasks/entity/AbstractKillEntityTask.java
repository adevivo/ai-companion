package com.player2.playerengine.tasks.entity;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.chains.MobDefenseChain;
import com.player2.playerengine.mixins.LivingEntityMixin;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.slots.PlayerSlot;
import java.util.List;
import java.util.OptionalDouble;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;

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

   /**
    * The attack-damage bonus an item carries, or empty when it grants none.
    *
    * <p>Both halves of this matter. On 1.20.1 this was
    * {@code item instanceof TieredItem t ? t.getTier().getAttackDamageBonus() : ...} — total, and
    * incapable of throwing. 1.21 removed tiers in favour of the attribute-modifiers component, and
    * the rewrite guarded on {@code components().has(ATTRIBUTE_MODIFIERS)} before calling
    * {@code .findFirst().get()} on the ATTACK_DAMAGE entry. Those are not the same question: armour
    * carries attribute modifiers and no attack damage, so the filter came back empty and
    * {@code Optional.get()} threw {@code NoSuchElementException} straight out of the AI tick.
    *
    * <p>Seen in the wild on 2026-08-23: a companion died, respawned with an empty inventory, and
    * the very next tick asked what its best weapon was. Five throws later its AI was disabled
    * outright and it stood still until the world was reloaded.
    */
   private static OptionalDouble attackDamageOf(Item candidate) {
      ItemAttributeModifiers modifiers = candidate.components().get(DataComponents.ATTRIBUTE_MODIFIERS);
      if (modifiers == null) {
         return OptionalDouble.empty();
      }
      return modifiers.modifiers().stream()
            .filter(entry -> entry.attribute() == Attributes.ATTACK_DAMAGE)
            .mapToDouble(entry -> entry.modifier().amount())
            .findFirst();
   }

   public static Item bestWeapon(PlayerEngineController mod) {
      Item toolItem1 = null;
      List<ItemStack> invStacks = mod.getItemStorage().getItemStacksPlayerInventory(true);
      Item toolItem2 = MobDefenseChain.getBestWeapon(mod);
      if (toolItem2 != null) {
         return toolItem2;
      } else {
         Item item = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot(mod.getInventory())).getItem();
         // Anything with no attack damage sits at -inf and so never wins, which is what the
         // TieredItem check used to accomplish by simply not matching.
         float bestDamage = (float) attackDamageOf(item).orElse(Double.NEGATIVE_INFINITY);

         for (ItemStack invStack : invStacks) {
            // invStack, NOT item. The 1.20.1 original read a pattern variable bound from invStack;
            // dropping the `instanceof TieredItem toolItem` pattern for a `.has(...)` test destroyed
            // that binding, and the equipped `item` — in scope, right type — was substituted for it.
            // Every iteration then scored the same stack, so "best weapon" returned whichever
            // inventory item happened to come last rather than the strongest one. Silent, and
            // invisible to the compiler.
            float itemDamage = (float) attackDamageOf(invStack.getItem()).orElse(Double.NEGATIVE_INFINITY);
            if (itemDamage > bestDamage) {
               toolItem1 = invStack.getItem();
               bestDamage = itemDamage;
            }
         }

         return toolItem1;
      }
   }

   /**
    * Put the best weapon in hand. Returns true when it is busy doing so, which the caller reads as
    * "not ready to swing this tick".
    *
    * <p>Yields while a mouthful is in progress. Eating is a 32-tick commitment during which the food
    * has to stay in hand, so a combat path re-equipping a sword every tick does not merely thrash the
    * hotbar — it cancels the bite outright, every time, and the companion never actually eats. Same
    * stand-down {@code PreEquipItemChain} already does. Returning true here also stops it swinging with
    * a lamb chop, and the weapon comes back the moment the bite finishes.
    */
   public static boolean equipWeapon(PlayerEngineController mod) {
      if (mod.getFoodChain() != null && mod.getFoodChain().isTryingToEat()) {
         return true;
      }

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
   protected Task onEntityInteract(PlayerEngineController mod, Entity entity) {
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
    * already applied in {@link com.player2.playerengine.control.KillAura#getAttackCooldownProgressPerTick}.
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
