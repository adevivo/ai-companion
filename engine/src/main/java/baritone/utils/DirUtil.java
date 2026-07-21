package baritone.utils;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public class DirUtil {
   public static Path getGameDir() {
      return FabricLoader.getInstance().getGameDir();
   }

   public static Path getConfigDir() {
      return FabricLoader.getInstance().getConfigDir();
   }
}
