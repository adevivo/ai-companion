package com.player2.playerengine.player2api.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Utils {
   public static String replacePlaceholders(String input, Map<String, String> replacements) {
      for (Entry<String, String> entry : replacements.entrySet()) {
         String placeholder = "\\{\\{" + entry.getKey() + "}}";
         input = input.replaceAll(placeholder, entry.getValue());
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
      content = content.replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "").trim();
      JsonParser parser = new JsonParser();
      return parser.parse(content).getAsJsonObject();
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
