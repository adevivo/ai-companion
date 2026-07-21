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

   /**
    * Forget every component. Must be called when a world stops — see
    * {@link EntityComponentKey#clear()} for the full reasoning. The same flaw applies here: the key is
    * a dimension {@code ResourceKey}, which is identical across sessions, while the cached component
    * holds the {@code Level} object from the session it was created in.
    */
   public final void clear() {
      this.storage.clear();
   }
}
