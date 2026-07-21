package adris.altoclef.util.helpers;

import adris.altoclef.AltoClefController;
import adris.altoclef.multiversion.DamageSourceWrapper;
import adris.altoclef.multiversion.MethodWrapper;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class EntityHelper {
   public static final double ENTITY_GRAVITY = 0.08;

   public static boolean isAngryAtPlayer(AltoClefController mod, Entity mob) {
      boolean hostile = isProbablyHostileToPlayer(mod, mob);
      return !(mob instanceof Mob entity) ? hostile : hostile && entity.getTarget() == mod.getPlayer();
   }

   public static boolean isProbablyHostileToPlayer(AltoClefController mod, Entity entity) {
      if (entity instanceof Mob mob) {
         if (mob instanceof Slime slime) {
            return slime.getAttributeValue(Attributes.ATTACK_DAMAGE) > 0.0;
         } else if (mob instanceof Piglin piglin) {
            return piglin.isAggressive() && !isTradingPiglin(mob) && piglin.isAdult();
         } else if (mob instanceof EnderMan enderman) {
            return enderman.isCreepy();
         } else {
            return mob instanceof ZombifiedPiglin zombifiedPiglin ? zombifiedPiglin.isAggressive() : mob.isAggressive() || mob instanceof Monster;
         }
      } else {
         return false;
      }
   }

   public static boolean isTradingPiglin(Entity entity) {
      if (entity instanceof Piglin pig && pig.getHandSlots() != null) {
         for (ItemStack stack : pig.getHandSlots()) {
            if (stack.getItem().equals(Items.GOLD_INGOT)) {
               return true;
            }
         }
      }

      return false;
   }

   public static double calculateResultingPlayerDamage(LivingEntity player, DamageSource src, double damageAmount) {
      DamageSourceWrapper source = DamageSourceWrapper.of(src);
      if (player.isInvulnerableTo(src)) {
         return 0.0;
      } else {
         if (!source.bypassesArmor()) {
            damageAmount = MethodWrapper.getDamageLeft(
               player, damageAmount, src, (double)player.getArmorValue(), player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
            );
         }

         if (!source.bypassesShield()) {
            if (player.hasEffect(MobEffects.DAMAGE_RESISTANCE) && source.isOutOfWorld()) {
               float k = (player.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
               float j = 25.0F - k;
               double f = damageAmount * j;
               damageAmount = Math.max(f / 25.0, 0.0);
            }

            if (damageAmount <= 0.0) {
               damageAmount = 0.0;
            } else {
               float k = EnchantmentHelper.getDamageProtection(player.getArmorSlots(), src);
               if (k > 0.0F) {
                  damageAmount = CombatRules.getDamageAfterMagicAbsorb((float)damageAmount, k);
               }
            }
         }

         return Math.max(damageAmount - player.getAbsorptionAmount(), 0.0);
      }
   }
}
