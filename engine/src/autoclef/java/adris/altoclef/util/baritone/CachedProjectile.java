package adris.altoclef.util.baritone;

import adris.altoclef.util.time.TimerGame;
import java.lang.reflect.Type;
import net.minecraft.world.phys.Vec3;

public class CachedProjectile {
   private final TimerGame lastCache = new TimerGame(2.0);
   public Vec3 velocity;
   public Vec3 position;
   public double gravity;
   public Type projectileType;
   private Vec3 cachedHit;
   private boolean cacheHeld = false;

   public Vec3 getCachedHit() {
      return this.cachedHit;
   }

   public void setCacheHit(Vec3 cache) {
      this.cachedHit = cache;
      this.cacheHeld = true;
      this.lastCache.reset();
   }

   public boolean needsToRecache() {
      return !this.cacheHeld || this.lastCache.elapsed();
   }
}
