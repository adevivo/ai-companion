package baritone.api.schematic.format;

import baritone.api.schematic.IStaticSchematic;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public interface ISchematicFormat {
   IStaticSchematic parse(InputStream var1) throws IOException;

   boolean isFileType(File var1);
}
