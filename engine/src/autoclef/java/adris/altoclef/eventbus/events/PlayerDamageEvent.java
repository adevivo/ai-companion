package adris.altoclef.eventbus.events;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

public class PlayerDamageEvent {
   public Entity target;
   public DamageSource source;
   public float damage;

   public PlayerDamageEvent(Entity target, DamageSource source, float damage) {
      this.target = target;
      this.source = source;
      this.damage = damage;
   }
}
