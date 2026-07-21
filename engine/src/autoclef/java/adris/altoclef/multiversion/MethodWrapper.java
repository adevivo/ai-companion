package adris.altoclef.multiversion;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;

public class MethodWrapper {
   public static Entity getRenderedEntity(BaseSpawner logic, Level world, BlockPos pos) {
      return logic.getOrCreateDisplayEntity(world, RandomSource.create(), pos);
   }

   public static float getDamageLeft(LivingEntity armorWearer, double damage, DamageSource source, double armor, double armorToughness) {
      return getDamageLeft(armorWearer, (float)damage, source, (float)armor, (float)armorToughness);
   }

   public static float getDamageLeft(LivingEntity armorWearer, float damage, DamageSource source, float armor, float armorToughness) {
      return CombatRules.getDamageAfterAbsorb(damage, armor, armorToughness);
   }
}
