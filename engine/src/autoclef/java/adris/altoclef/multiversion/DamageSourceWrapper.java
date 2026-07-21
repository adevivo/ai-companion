package adris.altoclef.multiversion;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

public class DamageSourceWrapper {
   private final DamageSource source;

   public static DamageSourceWrapper of(DamageSource source) {
      return source == null ? null : new DamageSourceWrapper(source);
   }

   private DamageSourceWrapper(DamageSource source) {
      this.source = source;
   }

   public DamageSource getSource() {
      return this.source;
   }

   public boolean bypassesArmor() {
      return this.source.is(DamageTypeTags.BYPASSES_ARMOR);
   }

   public boolean bypassesShield() {
      return this.source.is(DamageTypeTags.BYPASSES_SHIELD);
   }

   public boolean isOutOfWorld() {
      return this.source.is(DamageTypes.FELL_OUT_OF_WORLD);
   }
}
