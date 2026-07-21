package adris.altoclef.multiversion;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;

public class DamageSourceVer {
   public static DamageSource getFallDamageSource(Level world) {
      return world.damageSources().fall();
   }
}
