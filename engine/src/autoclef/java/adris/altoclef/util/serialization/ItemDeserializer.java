package adris.altoclef.util.serialization;

import adris.altoclef.Debug;
import adris.altoclef.util.helpers.ItemHelper;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class ItemDeserializer extends StdDeserializer<Object> {
   public ItemDeserializer() {
      this(null);
   }

   public ItemDeserializer(Class<Object> vc) {
      super(vc);
   }

   @Override
   public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
      List<Item> result = new ArrayList<>();
      if (p.getCurrentToken() != JsonToken.START_ARRAY) {
         throw new JsonParseException(p, "Start array expected");
      } else {
         while (p.nextToken() != JsonToken.END_ARRAY) {
            Item item = null;
            if (p.getCurrentToken() == JsonToken.VALUE_NUMBER_INT) {
               int rawId = p.getIntValue();
               item = Item.byId(rawId);
            } else {
               String itemKey = p.getText();
               itemKey = ItemHelper.trimItemName(itemKey);
               ResourceLocation identifier = new ResourceLocation(itemKey);
               if (BuiltInRegistries.ITEM.containsKey(identifier)) {
                  item = (Item)BuiltInRegistries.ITEM.get(identifier);
               } else {
                  Debug.logWarning("Invalid item name:" + itemKey + " at " + p.getCurrentLocation().toString());
               }
            }

            if (item != null) {
               result.add(item);
            }
         }

         return result;
      }
   }
}
