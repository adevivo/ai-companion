package adris.altoclef.util.serialization;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractVectorDeserializer<T, UnitType> extends StdDeserializer<T> {
   protected AbstractVectorDeserializer() {
      this(null);
   }

   protected AbstractVectorDeserializer(Class<T> vc) {
      super(vc);
   }

   protected abstract String getTypeName();

   protected abstract String[] getComponents();

   protected abstract UnitType parseUnit(String var1) throws Exception;

   protected abstract T deserializeFromUnits(List<UnitType> var1);

   protected abstract boolean isUnitTokenValid(JsonToken var1);

   UnitType trySet(JsonParser p, Map<String, UnitType> map, String key) throws JsonParseException {
      if (map.containsKey(key)) {
         return map.get(key);
      } else {
         throw new JsonParseException(p, this.getTypeName() + " should have key for " + this.getTypeName() + " key, but one was not found.");
      }
   }

   UnitType tryParse(JsonParser p, String whole, String part) throws JsonParseException {
      try {
         return this.parseUnit(part.trim());
      } catch (Exception var5) {
         throw new JsonParseException(p, "Failed to parse " + this.getTypeName() + " string \"" + whole + "\", specificaly part \"" + part + "\".");
      }
   }

   @Override
   public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      String[] neededComponents = this.getComponents();
      if (p.getCurrentToken() == JsonToken.VALUE_STRING) {
         String bposString = p.getValueAsString();
         String[] parts = bposString.split(",");
         if (parts.length != neededComponents.length) {
            throw new JsonParseException(
               p, "Invalid " + this.getTypeName() + " string: \"" + bposString + "\", must be in form \"" + String.join(",", neededComponents) + "\"."
            );
         } else {
            ArrayList<UnitType> resultingUnits = new ArrayList<>();

            for (String part : parts) {
               resultingUnits.add(this.tryParse(p, bposString, part));
            }

            return this.deserializeFromUnits(resultingUnits);
         }
      } else if (p.getCurrentToken() != JsonToken.START_OBJECT) {
         throw new JsonParseException(p, "Invalid token: " + p.getCurrentToken());
      } else {
         Map<String, UnitType> parts = new HashMap<>();
         p.nextToken();

         for (; p.getCurrentToken() != JsonToken.END_OBJECT; p.nextToken()) {
            if (p.getCurrentToken() != JsonToken.FIELD_NAME) {
               throw new JsonParseException(p, "Invalid structure, expected field name (like " + String.join(",", neededComponents) + ")");
            }

            p.nextToken();
            if (!this.isUnitTokenValid(p.currentToken())) {
               throw new JsonParseException(p, "Invalid token for " + this.getTypeName() + ". Got: " + p.getCurrentToken());
            }

            try {
               parts.put(p.getCurrentName(), this.parseUnit(p.getValueAsString()));
            } catch (Exception var11) {
               throw new JsonParseException(p, "Failed to parse unit " + p.getCurrentName());
            }
         }

         if (parts.size() != neededComponents.length) {
            throw new JsonParseException(
               p,
               "Expected ["
                  + String.join(",", neededComponents)
                  + "] keys to be part of a blockpos object. Got "
                  + Arrays.toString(parts.keySet().toArray(String[]::new))
            );
         } else {
            ArrayList<UnitType> resultingUnits = new ArrayList<>();

            for (String componentName : neededComponents) {
               resultingUnits.add(this.trySet(p, parts, componentName));
            }

            return this.deserializeFromUnits(resultingUnits);
         }
      }
   }
}
