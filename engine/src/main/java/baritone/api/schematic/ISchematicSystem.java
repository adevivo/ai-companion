package baritone.api.schematic;

import baritone.api.schematic.format.ISchematicFormat;
import java.io.File;
import java.util.List;
import java.util.Optional;

public interface ISchematicSystem {
   List<ISchematicFormat> getRegistry();

   Optional<ISchematicFormat> getByFile(File var1);
}
