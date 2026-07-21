package baritone.cache;

import baritone.api.cache.IWorldProvider;
import net.minecraft.world.level.Level;

public class WorldProvider implements IWorldProvider {
   private final WorldData currentWorld;

   public WorldProvider(Level world) {
      this.currentWorld = new WorldData(world.dimension());
   }

   public final WorldData getCurrentWorld() {
      return this.currentWorld;
   }
}
