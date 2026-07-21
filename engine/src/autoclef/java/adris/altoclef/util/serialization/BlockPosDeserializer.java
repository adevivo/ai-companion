package adris.altoclef.util.serialization;

import com.fasterxml.jackson.core.JsonToken;
import java.util.List;
import net.minecraft.core.BlockPos;

public class BlockPosDeserializer extends AbstractVectorDeserializer<BlockPos, Integer> {
   @Override
   protected String getTypeName() {
      return "BlockPos";
   }

   @Override
   protected String[] getComponents() {
      return new String[]{"x", "y", "z"};
   }

   protected Integer parseUnit(String unit) throws Exception {
      return Integer.parseInt(unit);
   }

   protected BlockPos deserializeFromUnits(List<Integer> units) {
      return new BlockPos(units.get(0), units.get(1), units.get(2));
   }

   @Override
   protected boolean isUnitTokenValid(JsonToken token) {
      return token == JsonToken.VALUE_NUMBER_INT;
   }
}
