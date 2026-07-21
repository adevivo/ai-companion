package baritone.utils.schematic;

import baritone.api.schematic.ISchematicSystem;
import baritone.api.schematic.format.ISchematicFormat;
import baritone.utils.schematic.format.DefaultSchematicFormats;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public enum SchematicSystem implements ISchematicSystem {
   INSTANCE;

   private final List<ISchematicFormat> registry = new ArrayList<>();

   private SchematicSystem() {
      Collections.addAll(this.registry, DefaultSchematicFormats.values());
   }

   @Override
   public List<ISchematicFormat> getRegistry() {
      return this.registry;
   }

   @Override
   public Optional<ISchematicFormat> getByFile(File file) {
      return this.registry.stream().filter(format -> format.isFileType(file)).findFirst();
   }
}
