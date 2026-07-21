package adris.altoclef.util.serialization;

import com.fasterxml.jackson.core.JsonToken;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public class Vec3dDeserializer extends AbstractVectorDeserializer<Vec3, Double> {
   @Override
   protected String getTypeName() {
      return "Vec3d";
   }

   @Override
   protected String[] getComponents() {
      return new String[]{"x", "y"};
   }

   protected Double parseUnit(String unit) throws Exception {
      return Double.parseDouble(unit);
   }

   protected Vec3 deserializeFromUnits(List<Double> units) {
      return new Vec3(units.get(0), units.get(1), units.get(2));
   }

   @Override
   protected boolean isUnitTokenValid(JsonToken token) {
      return token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT;
   }
}
