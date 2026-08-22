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

   /**
    * Messages added since the last write to disk. Replaces a {@code size() % 8 == 0} test that only
    * saved when the message count landed <em>exactly</em> on a multiple of eight — a coincidence check
    * rather than an interval. Messages arrive in pairs and batches, so a count that steps over a
    * multiple never comes back, and the file is then never written for the rest of the session.
    *
    * <p>Measured on 2026-08-17: a session of four exchanges wrote nothing at all, and quitting to the
    * title screen lost the whole conversation. The companion had been told "I like spruce, it looks
    * better than oak" and, four turns and one restart later, answered that oak was the player's
    * favourite. Even when the old check did fire it could lose up to seven messages.
    */
   private int unsavedMessages = 0;

   /** How many messages may accumulate before the history is written out. */
   private static final int SAVE_EVERY = 8;

   public ConversationHistory(String initialSystemPrompt, String characterName, String characterShortName) {
      this(initialSystemPrompt, characterName, characterShortName, null);
   }

   /**
    * A companion's history, kept on disk under the <b>owner's</b> directory.
    *
    * <p>⚠️ The file used to be named after the character alone — and named after it twice, because
    * {@code characterName} was interpolated where {@code characterShortName} was meant, which is how
    * {@code Ava_Ava.txt} got its name. Harmless in singleplayer. On a shared server it means two
    * players whose companions happen to share a roster name share one conversation file: each reads
    * the other's history at startup and both write over it. Now that a player brings their own
    * roster, sharing a name stops being a coincidence and becomes the likely case.
    *
    * <p>{@code owner} may be null for a companion spawned from the console, which has nobody to file
    * it under; those keep the flat legacy path so an existing save is not orphaned.
    *
    * @param owner the player this companion belongs to, or null
    */
   public ConversationHistory(String initialSystemPrompt, String characterName, String characterShortName,
         java.util.UUID owner) {
      // ServerPolicy.persistHistory off means this object never touches the disk at all: a null
      // historyFile is already the "carrier, not a conversation" case that every other constructor
      // relies on, so there is one no-persistence path rather than a second set of guards.
      if (!ServerPolicy.persistHistory) {
         this.historyFile = null;
         this.setBaseSystemPrompt(initialSystemPrompt);
         this.loadedFromFile = false;
         return;
      }
      Path configDir = DirUtil.getConfigDir();
      String safeName = characterName.replaceAll("\\s+", "_");
      this.historyFile = owner == null
            ? configDir.resolve(safeName + "_" + safeName + ".txt")
            : configDir.resolve("aicompanion").resolve("history").resolve(owner.toString())
                  .resolve(safeName + ".txt");
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
         // .isEmpty(), not == "": summarizeHistory returns the "" literal on failure, which happens to
         // be interned and so happens to compare equal, but an empty string from the API would not.
         if (summary.isEmpty()) {
            this.conversationHistory.remove(1);
         } else {
            JsonObject systemPrompt = this.conversationHistory.get(0);
            int tailStart = this.conversationHistory.size() - 16;
            List<JsonObject> tail = new ArrayList<>(
                  this.conversationHistory.subList(tailStart, this.conversationHistory.size()));
            this.conversationHistory.clear();
            this.conversationHistory.add(systemPrompt);
            JsonObject summaryMsg = new JsonObject();
            // "system", not "assistant". Written as an assistant turn, the summary is a third layer
            // of the companion reading its own words back as its own speech — the same failure the
            // grounding guard exists to stop in the store, in the one place that guard cannot see.
            // Measured on 2026-08-20: a fact the player stated aged out under the prompt budget and
            // only the companion's paraphrases of it survived, so it went on citing itself as the
            // source. As a system line it reads as notes about the conversation, which is what it is.
            summaryMsg.addProperty("role", "system");
            summaryMsg.addProperty("content", "Summary of earlier events: " + summary);
            this.conversationHistory.add(summaryMsg);
            this.conversationHistory.addAll(tail);
         }

         if (this.historyFile != null) {
            this.saveToFile();
         }
      } else if (doCutOff && this.historyFile != null && ++this.unsavedMessages >= SAVE_EVERY) {
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

   /**
    * Write the history out now, whatever the interval says.
    *
    * <p>For shutdown. The periodic save above bounds how much a crash can lose; this is what makes an
    * orderly quit lose nothing, and quitting to the title screen is the common case — a singleplayer
    * world stops its server every time.
    */
   public void flush() {
      if (this.historyFile != null && this.unsavedMessages > 0) {
         this.saveToFile();
      }
   }

   private void saveToFile() {
      this.unsavedMessages = 0;
      try {
         // The path is nested under the owner now, so the directory may not exist. Without this the
         // first write throws NoSuchFileException, the stack trace goes to stderr, and the history
         // is simply never written — the same silent no-op the periodic-save bug produced.
         Path parent = this.historyFile.getParent();
         if (parent != null) {
            Files.createDirectories(parent);
         }
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
      int before = total;
      // Walk forward from the oldest middle message, keeping index 0 and the final entry.
      while (total > maxChars && kept.size() > 2) {
         total -= contentLength(kept.remove(1));
         dropped++;
      }
      // Both sizes, because the pair is what diagnoses this: a big "before" with a small "after" is a
      // long tail of history, while a "before" barely above budget that will not come down is one
      // oversized turn that dropping cannot fix. A previous version passed the word "still" into the
      // {} that should have held a number, so the line read "prompt was still chars over the 16000
      // budget" — the warning survived and its only datum did not.
      if (total > maxChars) {
         // Dropping every droppable turn was not enough, which means the two messages this method may
         // never drop are together over budget on their own. That is not a trimming failure and no
         // amount of history-shedding will fix it: either the system prompt has outgrown the budget or
         // the budget is set below what one turn costs. Said explicitly because the generic line above
         // reads like the trimmer is broken, and the operator would go looking in the wrong place.
         // Measured 2026-08-17: system prompt 14,902 + newest wrapped turn 1,664 = 16,566 against a
         // 16,000 budget, so this fired on every single turn and all conversation history was being
         // discarded to no effect.
         LOGGER.warn("ConversationHistory: prompt was {} chars against a {} budget — dropped ALL {} "
                     + "droppable turn(s) and it is STILL {} chars. The system prompt ({}) plus the "
                     + "newest turn ({}) are {} on their own, which this method may not drop, so "
                     + "history cannot get under the budget: raise llm.maxPromptChars above {} or "
                     + "shorten the system prompt.",
               before, maxChars, dropped, total,
               contentLength(kept.get(0)), contentLength(kept.get(kept.size() - 1)), total, total);
      } else {
         LOGGER.warn("ConversationHistory: prompt was {} chars against a {} budget — dropped {} oldest "
                     + "turn(s), {} remain at {} chars. Long prompts make some models answer in prose "
                     + "instead of JSON, which runs no command.",
               before, maxChars, dropped, kept.size(), total);
      }
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
      return copyThenWrapLatestWithStatus(worldStatus, agentStatus, altoclefStatusMsgs,
            player2apiService, reminderString, java.util.List.of());
   }

   /**
    * As above, plus any memories retrieved for this turn.
    *
    * <p>Memory is one more field in the packet the brain already assembles, which is why "give the
    * companion a memory" needs no cooperation from the model: it never asks for a memory and never
    * learns that it could. The relevant facts are simply already in the context, next to the world
    * state, by the time it reads anything.
    *
    * <p>An empty list adds no field at all, so a turn that recalls nothing produces exactly the
    * prompt it would have produced before memory existed.
    */
   public ConversationHistory copyThenWrapLatestWithStatus(String worldStatus, String agentStatus,
         String altoclefStatusMsgs, Player2APIService player2apiService, Optional<String> reminderString,
         java.util.List<String> memories) {
      ConversationHistory copy = new ConversationHistory(this.conversationHistory.get(0).get("content").getAsString());
      List<JsonObject> wrapped = wrapLatest(this.conversationHistory, worldStatus, agentStatus,
            altoclefStatusMsgs, reminderString.orElse(null), memories);
      for (int i = 1; i < wrapped.size(); i++) {
         copy.addHistory(wrapped.get(i), false, player2apiService);
      }
      return copy;
   }

   /**
    * Fold this turn's status into the newest user message, as a pure function.
    *
    * <p>Extracted from {@link #copyThenWrapLatestWithStatus} so that <b>the same assembly runs
    * wherever the prompt is built</b>. Once a client does its companion's thinking it has to inject
    * its own memories here, and the only alternatives were string surgery on an already-assembled
    * prompt or a second copy of these rules — and a second copy would drift, silently, since nothing
    * compares the two.
    *
    * <p>Static and free of {@code Player2APIService} on purpose: the original passed one to
    * {@code addHistory}, which ignores it whenever {@code doCutOff} is false, and a client has no
    * server-side service to hand it.
    *
    * @param raw the full history, system prompt first; not modified
    * @return a new list, deep-copied below index 0
    */
   /**
    * Wrap an already-assembled message list so it can be handed to {@link Player2APIService}.
    *
    * <p>For a client that received raw history over the wire and assembled the prompt itself. It is
    * a carrier, not a conversation: nothing is persisted, summarised or trimmed, because the server
    * owns the real history and this copy exists for the length of one call.
    */
   public static ConversationHistory of(List<JsonObject> messages) {
      ConversationHistory h = new ConversationHistory(
            messages.isEmpty() ? "" : messages.get(0).get("content").getAsString());
      for (int i = 1; i < messages.size(); i++) {
         h.conversationHistory.add(messages.get(i));
      }
      return h;
   }

   public static List<JsonObject> wrapLatest(List<JsonObject> raw, String worldStatus,
         String agentStatus, String altoclefStatusMsgs, String reminder,
         java.util.List<String> memories) {
      List<JsonObject> out = new ArrayList<>(raw.size());
      if (raw.isEmpty()) {
         return out;
      }
      out.add(raw.get(0));
      for (int i = 1; i < raw.size() - 1; i++) {
         out.add(Utils.deepCopy(raw.get(i)));
      }
      if (raw.size() > 1) {
         JsonObject last = Utils.deepCopy(raw.get(raw.size() - 1));
         if ("user".equals(last.get("role").getAsString())) {
            String originalContent = last.get("content").getAsString();
            ObjectStatus msgObj = new ObjectStatus();
            msgObj.add("userMessage", originalContent);
            if (reminder != null) {
               msgObj.add("reminders", reminder);
            }
            msgObj.add("worldStatus", worldStatus);
            msgObj.add("agentStatus", agentStatus);
            if (altoclefStatusMsgs != null && !altoclefStatusMsgs.isBlank()) {
               msgObj.add("gameDebugMessages", altoclefStatusMsgs);
            }
            if (memories != null && !memories.isEmpty()) {
               // Presented as things already known rather than as search results. "Here is what you
               // remember" invites the model to answer the question; a labelled retrieval dump
               // invites it to talk about the retrieval.
               msgObj.add("memories", String.join(" | ", memories));
            }
            last.addProperty("content", msgObj.toString());
         }
         out.add(last);
      }
      return out;
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