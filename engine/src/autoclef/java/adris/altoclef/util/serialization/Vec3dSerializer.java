package adris.altoclef.util.serialization;

import java.util.Arrays;
import java.util.Collection;
import net.minecraft.world.phys.Vec3;

public class Vec3dSerializer extends AbstractVectorSerializer<Vec3> {
   protected Collection<String> getParts(Vec3 value) {
      return Arrays.asList(value.x() + "", value.y() + "", value.z() + "");
   }
}
