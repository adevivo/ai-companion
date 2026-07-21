package baritone.api.cache;

import baritone.api.component.WorldComponentKey;
import baritone.cache.WorldProvider;

public interface IWorldProvider {
   WorldComponentKey<IWorldProvider> KEY = new WorldComponentKey<>(WorldProvider::new);

   IWorldData getCurrentWorld();
}
