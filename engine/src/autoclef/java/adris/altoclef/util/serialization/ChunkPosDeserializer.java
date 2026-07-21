package adris.altoclef.util.serialization;

import com.fasterxml.jackson.core.JsonToken;
import java.util.List;
import net.minecraft.world.level.ChunkPos;

public class ChunkPosDeserializer extends AbstractVectorDeserializer<ChunkPos, Integer> {
   @Override
   protected String getTypeName() {
      return "ChunkPos";
   }

   @Override
   protected String[] getComponents() {
      return new String[]{"x", "z"};
   }

   protected Integer parseUnit(String unit) throws Exception {
      return Integer.parseInt(unit);
   }

   protected ChunkPos deserializeFromUnits(List<Integer> units) {
      return new ChunkPos(units.get(0), units.get(1));
   }

   @Override
   protected boolean isUnitTokenValid(JsonToken unitToken) {
      return false;
   }
}
