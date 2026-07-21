package adris.altoclef.player2api.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Utils {
   public static String replacePlaceholders(String input, Map<String, String> replacements) {
      for (Entry<String, String> entry : replacements.entrySet()) {
         String placeholder = "\\{\\{" + entry.getKey() + "}}";
         // Values come from user config (persona/description) — quote so literal $ and \ are not
         // interpreted as regex replacement backreferences (would throw or corrupt the prompt).
         input = input.replaceAll(placeholder, java.util.regex.Matcher.quoteReplacement(entry.getValue()));
      }

      return input;
   }

   public static String getStringJsonSafely(JsonObject input, String fieldName) {
      return input.has(fieldName) && !input.get(fieldName).isJsonNull() ? input.get(fieldName).getAsString() : null;
   }

   public static String[] jsonArrayToStringArray(JsonArray jsonArray) {
      if (jsonArray == null) {
         return new String[0];
      } else {
         List<String> stringList = new ArrayList<>();

         for (JsonElement element : jsonArray) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
               stringList.add(element.getAsString());
            } else {
               System.err.println("Warning: Skipping non-string element in JSON array: " + element);
            }
         }

         return stringList.toArray(new String[0]);
      }
   }

   public static String[] getStringArrayJsonSafely(JsonObject input, String fieldName) {
      if (input.has(fieldName) && !input.get(fieldName).isJsonNull()) {
         JsonElement element = input.get(fieldName);
         if (!element.isJsonArray()) {
            System.err
                  .println("Warning: Expected a JSON array for field '" + fieldName + "', but found a different type.");
            return null;
         } else {
            JsonArray jsonArray = element.getAsJsonArray();
            return jsonArrayToStringArray(jsonArray);
         }
      } else {
         return null;
      }
   }

   public static JsonObject parseCleanedJson(String content) throws JsonSyntaxException {
      String cleaned = content == null ? "" : content.trim();
      // Strip markdown code fences: ```json ... ``` or bare ``` ... ```.
      cleaned = cleaned.replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("(?s)\\s*```\\s*$", "").trim();
      // Models sometimes wrap the JSON in reasoning/prose (e.g. a <think> preamble). Keep only the
      // outermost {...} object so leading/trailing text does not break parsing.
      int start = cleaned.indexOf('{');
      int end = cleaned.lastIndexOf('}');
      if (start >= 0 && end > start) {
         cleaned = cleaned.substring(start, end + 1);
      }
      // Lenient reader tolerates minor deviations (unquoted control chars, trailing commas, etc.).
      JsonReader reader = new JsonReader(new StringReader(cleaned));
      reader.setLenient(true);
      return new JsonParser().parse(reader).getAsJsonObject();
   }

   public static String[] splitLinesToArray(String input) {
      return input != null && !input.isEmpty() ? input.split("\\R+") : new String[0];
   }

   public static JsonObject deepCopy(JsonObject original) {
      JsonParser parser = new JsonParser();
      return parser.parse(original.toString()).getAsJsonObject();
   }

   @FunctionalInterface
   public interface ThrowingFunction<T, R> {
      R apply(T t) throws Exception;
   }
}
