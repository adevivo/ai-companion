package adris.altoclef.player2api;

import adris.altoclef.player2api.status.ObjectStatus;
import adris.altoclef.player2api.utils.Utils;
import baritone.utils.DirUtil;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConversationHistory {
   private static final Logger LOGGER = LogManager.getLogger();
   private final List<JsonObject> conversationHistory = new ArrayList<>();
   private final Path historyFile;
   private boolean loadedFromFile = false;
   private static final int MAX_HISTORY = 64;
   private static final int SUMMARY_COUNT = 48;

   public ConversationHistory(String initialSystemPrompt, String characterName, String characterShortName) {
      Path configDir = DirUtil.getConfigDir();
      String fileName = characterName.replaceAll("\\s+", "_") + "_" + characterName.replaceAll("\\s+", "_") + ".txt";
      this.historyFile = configDir.resolve(fileName);
      if (Files.exists(this.historyFile)) {
         this.loadFromFile();
         this.setBaseSystemPrompt(initialSystemPrompt);
         this.loadedFromFile = true;
      } else {
         this.setBaseSystemPrompt(initialSystemPrompt);
         this.loadedFromFile = false;
      }
   }

   public ConversationHistory(String initialSystemPrompt) {
      this.historyFile = null;
      this.setBaseSystemPrompt(initialSystemPrompt);
      this.loadedFromFile = false;
   }

   public boolean isLoadedFromFile() {
      return this.loadedFromFile;
   }

   public void addHistory(JsonObject text, boolean doCutOff, Player2APIService player2apiService) {
      this.conversationHistory.add(text);
      if (doCutOff && this.conversationHistory.size() > 64) {
         List<JsonObject> toSummarize = new ArrayList<>(this.conversationHistory.subList(1, 49));
         String summary = this.summarizeHistory(toSummarize, player2apiService);
         if (summary == "") {
            this.conversationHistory.remove(1);
         } else {
            JsonObject systemPrompt = this.conversationHistory.get(0);
            int tailStart = this.conversationHistory.size() - 16;
            List<JsonObject> tail = new ArrayList<>(
                  this.conversationHistory.subList(tailStart, this.conversationHistory.size()));
            this.conversationHistory.clear();
            this.conversationHistory.add(systemPrompt);
            JsonObject summaryMsg = new JsonObject();
            summaryMsg.addProperty("role", "assistant");
            summaryMsg.addProperty("content", "Summary of earlier events: " + summary);
            this.conversationHistory.add(summaryMsg);
            this.conversationHistory.addAll(tail);
         }

         if (this.historyFile != null) {
            this.saveToFile();
         }
      } else if (doCutOff && this.conversationHistory.size() % 8 == 0 && this.historyFile != null) {
         this.saveToFile();
      }
   }

   private String summarizeHistory(List<JsonObject> messages, Player2APIService player2apiService) {
      String summarizationPrompt = "    Our AI agent that has been chatting with user and playing minecraft.\n    Update agent's memory by summarizing the following conversation in the next response.\n\n    Use natural language, not JSON format.\n\n    Prioritize preserving important facts, things user asked agent to remember, useful tips.\n    Do not record stats, inventory, code or docs; limit to 500 chars.\n";
      ConversationHistory temp = new ConversationHistory(summarizationPrompt);

      for (JsonObject msg : messages) {
         temp.addHistory(Utils.deepCopy(msg), false, player2apiService);
      }

      try {
         String resp = player2apiService.completeConversationToString(temp);
         return resp;
      } catch (Exception var6) {
         var6.printStackTrace();
         System.err.println("Error communicating with API");
         return "";
      }
   }

   private void saveToFile() {
      try {
         BufferedWriter writer = Files.newBufferedWriter(this.historyFile);

         try {
            for (JsonObject msg : this.conversationHistory) {
               writer.write(msg.toString());
               writer.newLine();
            }

            if (writer != null) {
               writer.close();
            }
         } catch (Throwable var5) {
            if (writer != null) {
               try {
                  writer.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }
      } catch (IOException var6) {
         var6.printStackTrace();
      }
   }

   private void loadFromFile() {
      List<JsonObject> loaded = new ArrayList<>();

      try {
         BufferedReader reader = Files.newBufferedReader(this.historyFile);

         try {
            String line;
            while ((line = reader.readLine()) != null) {
               JsonObject obj = Utils.parseCleanedJson(line);
               if (obj.has("content")) {
                  String content = obj.get("content").getAsString();
                  if (content.length() > 500) {
                     obj.addProperty("content", content.substring(0, 500));
                  }
               }

               loaded.add(obj);
               if (loaded.size() > 64) {
                  break;
               }
            }

            this.conversationHistory.clear();
            this.conversationHistory.addAll(loaded);
            if (reader != null) {
               reader.close();
            }
         } catch (Throwable var7) {
            if (reader != null) {
               try {
                  reader.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }
            }

            throw var7;
         }
      } catch (IOException var8) {
         var8.printStackTrace();
         this.conversationHistory.clear();
      }
   }

   public void addUserMessage(String userText, Player2APIService player2apiService) {
      JsonObject objectToAdd = new JsonObject();
      objectToAdd.addProperty("role", "user");
      objectToAdd.addProperty("content", userText);
      this.addHistory(objectToAdd, false, player2apiService);
   }

   public void setBaseSystemPrompt(String newPrompt) {
      if (!this.conversationHistory.isEmpty()
            && "system".equals(this.conversationHistory.get(0).get("role").getAsString())) {
         this.conversationHistory.get(0).addProperty("content", newPrompt);
      } else {
         JsonObject systemMessage = new JsonObject();
         systemMessage.addProperty("role", "system");
         systemMessage.addProperty("content", newPrompt);
         this.conversationHistory.add(0, systemMessage);
      }
   }

   public void addSystemMessage(String systemText, Player2APIService player2apiService) {
      JsonObject objectToAdd = new JsonObject();
      objectToAdd.addProperty("role", "system");
      objectToAdd.addProperty("content", systemText);
      this.addHistory(objectToAdd, false, player2apiService);
   }

   public void addAssistantMessage(String messageText, Player2APIService player2apiService) {
      JsonObject objectToAdd = new JsonObject();
      objectToAdd.addProperty("role", "assistant");
      objectToAdd.addProperty("content", messageText);
      this.addHistory(objectToAdd, true, player2apiService);
   }

   public List<JsonObject> getListJSON() {
      return this.conversationHistory;
   }

   /**
     * The history trimmed to fit {@code maxChars}, oldest turns dropped first.
     *
     * <p>{@link #MAX_HISTORY} bounds the number of messages, which turns out not to bound the prompt:
     * every turn carries a full world/agent status blob, and the {@code nearby blocks} map alone can
     * run to thousands of entries, so 64 messages is anywhere from 13k to 25k characters.
     *
     * <p>That matters because the format contract lives at the <em>front</em> of the prompt. Once the
     * whole thing outgrows what the served model can attend to, the contract is what gets lost: the
     * model still reasons correctly off the recent turns at the tail and picks sensible commands, but
     * emits them as prose instead of the JSON envelope, so nothing runs. Measured against one local
     * model, replies that parsed had a median prompt of ~16.6k characters and replies that failed
     * ~19.5k, with total failure around 24k.
     *
     * <p>The system prompt and the newest message are never dropped — the first carries the contract
     * and the second is what is actually being asked.
     */
   public List<JsonObject> getListJSONBounded(int maxChars) {
      if (maxChars <= 0 || this.conversationHistory.size() <= 2) {
         return this.conversationHistory;
      }
      int total = 0;
      for (JsonObject msg : this.conversationHistory) {
         total += contentLength(msg);
      }
      if (total <= maxChars) {
         return this.conversationHistory;
      }

      List<JsonObject> kept = new ArrayList<>(this.conversationHistory);
      int dropped = 0;
      // Walk forward from the oldest middle message, keeping index 0 and the final entry.
      while (total > maxChars && kept.size() > 2) {
         total -= contentLength(kept.remove(1));
         dropped++;
      }
      LOGGER.warn("ConversationHistory: prompt was {} chars over the {} budget — dropped {} oldest "
                  + "turn(s), {} remain. Long prompts make some models answer in prose instead of JSON, "
                  + "which runs no command.",
            total > maxChars ? "still" : "", maxChars, dropped, kept.size());
      return kept;
   }

   private static int contentLength(JsonObject msg) {
      return msg.has("content") && msg.get("content").isJsonPrimitive()
            ? msg.get("content").getAsString().length()
            : 0;
   }

   // ReminderString adds a reminder to the latest user message if present.
   public ConversationHistory copyThenWrapLatestWithStatus(String worldStatus, String agentStatus,
         String altoclefStatusMsgs, Player2APIService player2apiService, Optional<String> reminderString) {
      ConversationHistory copy = new ConversationHistory(this.conversationHistory.get(0).get("content").getAsString());

      for (int i = 1; i < this.conversationHistory.size() - 1; i++) {
         copy.addHistory(Utils.deepCopy(this.conversationHistory.get(i)), false, player2apiService);
      }

      if (this.conversationHistory.size() > 1) {
         JsonObject last = Utils.deepCopy(this.conversationHistory.get(this.conversationHistory.size() - 1));
         if ("user".equals(last.get("role").getAsString())) {
            String originalContent = last.get("content").getAsString();
            ObjectStatus msgObj = new ObjectStatus();
            msgObj.add("userMessage", originalContent);
            reminderString.ifPresent(remind -> {
               msgObj.add("reminders", remind);
            });
            msgObj.add("worldStatus", worldStatus);
            msgObj.add("agentStatus", agentStatus);
            if (!altoclefStatusMsgs.isBlank()) {
               msgObj.add("gameDebugMessages", altoclefStatusMsgs);
            }
            last.addProperty("content", msgObj.toString());
         }

         copy.addHistory(last, false, player2apiService);
      }

      return copy;
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("ConversationHistory {\n");

      for (JsonObject message : this.conversationHistory) {
         String role = message.has("role") ? message.get("role").getAsString() : "unknown";
         String content = message.has("content") ? message.get("content").getAsString() : "";
         sb.append("  [").append(role).append("] ").append(content).append("\n");
      }

      sb.append("}");
      return sb.toString();
   }

   public void clear() {
      if (!this.conversationHistory.isEmpty()) {
         JsonObject systemPrompt = this.conversationHistory.get(0);
         this.conversationHistory.clear();
         this.conversationHistory.add(systemPrompt);
      }

      if (this.historyFile != null) {
         try {
            Files.deleteIfExists(this.historyFile);
         } catch (IOException var2) {
            var2.printStackTrace();
         }
      }
   }
}