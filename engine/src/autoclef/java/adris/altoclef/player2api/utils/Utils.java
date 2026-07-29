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

   /** Reasoning wrappers emitted inline by thinking models, stripped before we look for JSON. */
   private static final String REASONING_BLOCK =
         "(?is)<\\s*(think|thinking|reasoning|scratchpad)\\s*>.*?<\\s*/\\s*\\1\\s*>";
   /** An opening reasoning tag the model never closed — everything after it is thinking. */
   private static final String UNCLOSED_REASONING = "(?is)<\\s*(think|thinking|reasoning|scratchpad)\\s*>.*$";

   public static JsonObject parseCleanedJson(String content) throws JsonSyntaxException {
      String cleaned = content == null ? "" : content.trim();
      cleaned = stripReasoning(cleaned);
      // Strip markdown code fences: ```json ... ``` or bare ``` ... ```.
      cleaned = cleaned.replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("(?s)\\s*```\\s*$", "").trim();
      cleaned = extractJsonObject(cleaned);
      // Lenient reader tolerates minor deviations (unquoted control chars, trailing commas, etc.).
      JsonReader reader = new JsonReader(new StringReader(cleaned));
      reader.setLenient(true);
      return new JsonParser().parse(reader).getAsJsonObject();
   }

   /**
    * Removes inline reasoning that thinking models mix into the content field. Must run before the
    * JSON is located: a stray brace inside a {@code <think>} block would otherwise be mistaken for
    * the start of the response object, and the whole reply — reasoning and all — would end up
    * spoken in chat.
    */
   /**
    * If the reply is a bare JSON string rather than the expected object, returns its unquoted text.
    *
    * <p>Models do this under {@code response_format: json_object} — a quoted string satisfies "valid
    * JSON" without being an object, so the mode does not prevent it. Without unwrapping, the raw
    * content is spoken with its quote marks still attached ({@code <name> "Water ready—…"}).
    *
    * @return the unwrapped text, or null when the content is not a lone JSON string
    */
   public static String unwrapJsonString(String content) {
      if (content == null || content.isBlank()) {
         return null;
      }
      String cleaned = stripReasoning(content).trim();
      if (!cleaned.startsWith("\"")) {
         return null;
      }
      try {
         JsonReader reader = new JsonReader(new StringReader(cleaned));
         reader.setLenient(true);
         JsonElement parsed = new JsonParser().parse(reader);
         if (parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isString()) {
            return parsed.getAsString();
         }
      } catch (RuntimeException e) {
         // Starts with a quote but is not parseable JSON — leave it to the caller's own handling.
      }
      return null;
   }

   public static String stripReasoning(String content) {
      if (content == null || content.isEmpty()) {
         return "";
      }
      String stripped = content.replaceAll(REASONING_BLOCK, " ");
      // An unclosed tag means the model was cut off mid-thought (usually maxTokens). Drop the tail,
      // but only if doing so still leaves us something — otherwise keep the text for the caller to
      // judge, since dropping everything would turn a truncated reply into silence with no clue why.
      String withoutUnclosed = stripped.replaceAll(UNCLOSED_REASONING, " ").trim();
      if (!withoutUnclosed.isEmpty()) {
         stripped = withoutUnclosed;
      }
      return stripped.trim();
   }

   /**
    * Returns the first balanced {@code {...}} object in {@code text}, or the text unchanged when
    * there is none. Brace-aware and string-aware, so braces inside JSON string values (or in prose
    * around the object) do not throw off the bounds the way a plain first-brace/last-brace scan does.
    */
   private static String extractJsonObject(String text) {
      int depth = 0;
      int start = -1;
      boolean inString = false;
      boolean escaped = false;
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (inString) {
            if (escaped) {
               escaped = false;
            } else if (c == '\\') {
               escaped = true;
            } else if (c == '"') {
               inString = false;
            }
            continue;
         }
         if (c == '"') {
            inString = true;
         } else if (c == '{') {
            if (depth == 0) {
               start = i;
            }
            depth++;
         } else if (c == '}') {
            depth--;
            if (depth == 0 && start >= 0) {
               return text.substring(start, i + 1);
            }
            if (depth < 0) { // stray closing brace in prose — resync
               depth = 0;
               start = -1;
            }
         }
      }
      // Unbalanced (truncated reply): fall back to the widest span so a lenient parse can still try.
      int first = text.indexOf('{');
      int last = text.lastIndexOf('}');
      return first >= 0 && last > first ? text.substring(first, last + 1) : text;
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
