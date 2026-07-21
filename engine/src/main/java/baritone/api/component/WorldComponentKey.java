package baritone.api.component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class WorldComponentKey<C> {
   private final Map<ResourceKey<Level>, C> storage = new HashMap<>();
   private final Function<Level, C> factory;

   public WorldComponentKey(Function<Level, C> factory) {
      this.factory = factory;
   }

   public final C get(Level provider) {
      return this.storage.computeIfAbsent(provider.dimension(), u -> this.factory.apply(provider));
   }
}
