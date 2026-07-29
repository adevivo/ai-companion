package adris.altoclef.player2api;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.auth.AuthKey;
import adris.altoclef.player2api.auth.AuthenticationManager;
import adris.altoclef.player2api.manager.HeartbeatManager;
import adris.altoclef.player2api.utils.CharacterUtils;
import adris.altoclef.player2api.utils.HTTPUtils;
import adris.altoclef.player2api.utils.HttpApiException;
import adris.altoclef.player2api.utils.Player2HTTPUtils;
import adris.altoclef.player2api.utils.Utils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.net.HttpURLConnection;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import java.util.HashMap;

public class Player2APIService {
   private static final Logger LOGGER = LogManager.getLogger();

   /** Counts LLM requests this session for the {@link LlmConfig#maxRequests} cost guardrail. */
   private static final java.util.concurrent.atomic.AtomicInteger requestCount =
         new java.util.concurrent.atomic.AtomicInteger(0);

   // Cumulative token usage this session, summed from the `usage` object OpenAI-compatible servers
   // return. Static (not per-companion) because what a player wants to know is total session spend.
   private static final java.util.concurrent.atomic.AtomicLong promptTokens =
         new java.util.concurrent.atomic.AtomicLong(0);
   private static final java.util.concurrent.atomic.AtomicLong completionTokens =
         new java.util.concurrent.atomic.AtomicLong(0);
   private static final java.util.concurrent.atomic.AtomicLong totalTokens =
         new java.util.concurrent.atomic.AtomicLong(0);
   /** Highest {@code totalTokens / usageReportEveryTokens} milestone already reported. */
   private static final java.util.concurrent.atomic.AtomicLong reportedMilestone =
         new java.util.concurrent.atomic.AtomicLong(0);

   private String clientId;
   private AltoClefController controller;

   public Player2APIService(AltoClefController controller, String clientId) {
      this.clientId = clientId;
      this.controller = controller;
   }

   /**
    * Apply the config-driven sampling params from {@link LlmConfig} to an OpenAI-compatible request body.
    * Each is omitted when unset (blank model / sentinel temperature &lt; 0 / maxTokens &lt;= 0) so the server
    * falls back to its own defaults.
    */
   /**
    * Enforce the {@link LlmConfig#maxRequests} cost guardrail. Throws before any HTTP call once the cap
    * is exceeded, so a runaway loop cannot keep spending on a paid endpoint. {@code <= 0} = unlimited.
    */
   private static void enforceRequestCap() throws Exception {
      // Count every request, cap or no cap — the usage report reads this counter too.
      int n = requestCount.incrementAndGet();
      int max = LlmConfig.maxRequests;
      if (max <= 0) {
         return;
      }
      if (n > max) {
         throw new Exception("LLM request cap reached (" + max
               + "). Raise llm.maxRequests or restart the server to continue (cost guardrail).");
      }
      LOGGER.info("LLM request {}/{} (cost guardrail)", n, max);
   }

   /**
    * Reset the per-session request count and token totals. Called when a world stops, which makes
    * "session" mean "world session" — exactly what {@link LlmConfig#maxRequests} already promises the
    * player ("restart the world to continue").
    */
   public static void resetSessionCounters() {
      requestCount.set(0);
      promptTokens.set(0);
      completionTokens.set(0);
      totalTokens.set(0);
      reportedMilestone.set(0);
   }

   /** Immutable view of this session's LLM spend. */
   public record UsageSnapshot(long promptTokens, long completionTokens, long totalTokens, int requests) {}

   /**
    * Read the session counters for display. The four values are read independently, so a snapshot
    * taken mid-{@link #recordUsage} can show a total that lags its parts by one request — acceptable
    * for a HUD that refreshes every second, and not worth a lock on the LLM path.
    */
   public static UsageSnapshot usageSnapshot() {
      return new UsageSnapshot(promptTokens.get(), completionTokens.get(), totalTokens.get(),
            requestCount.get());
   }

   /**
    * Accumulate token usage from an OpenAI-compatible response and, every
    * {@link LlmConfig#usageReportEveryTokens} tokens, tell the owner where they stand. Purely
    * informational — it never blocks a call. Servers that omit {@code usage} (some proxies do) simply
    * contribute nothing rather than erroring.
    */
   private void recordUsage(Map<String, JsonElement> responseMap) {
      try {
         JsonElement usageEl = responseMap.get("usage");
         if (usageEl == null || !usageEl.isJsonObject()) {
            return;
         }
         JsonObject usage = usageEl.getAsJsonObject();
         long in = usage.has("prompt_tokens") ? usage.get("prompt_tokens").getAsLong() : 0;
         long out = usage.has("completion_tokens") ? usage.get("completion_tokens").getAsLong() : 0;
         // Prefer the server's own total; fall back to the parts when it isn't reported.
         long tot = usage.has("total_tokens") ? usage.get("total_tokens").getAsLong() : in + out;

         promptTokens.addAndGet(in);
         completionTokens.addAndGet(out);
         long runningTotal = totalTokens.addAndGet(tot);

         long every = LlmConfig.usageReportEveryTokens;
         if (every <= 0) {
            return;
         }
         long milestone = runningTotal / every;
         long alreadyReported = reportedMilestone.get();
         // CAS so concurrent completions can't double-report the same milestone.
         if (milestone > alreadyReported && reportedMilestone.compareAndSet(alreadyReported, milestone)) {
            reportUsage(runningTotal);
         }
      } catch (Exception e) {
         // Usage reporting must never break a working conversation.
         LOGGER.debug("Could not record LLM token usage: {}", e.toString());
      }
   }

   private void reportUsage(long runningTotal) {
      String summary = String.format(
            "LLM usage this session: %,d tokens (%,d in / %,d out) over %,d requests.",
            runningTotal, promptTokens.get(), completionTokens.get(), requestCount.get());
      LOGGER.info(summary);
      if (controller != null && controller.getOwner() instanceof ServerPlayer owner) {
         owner.displayClientMessage(Component.literal("[companion] " + summary), false);
      }
   }

   /**
    * Shared model/sampling parameters.
    *
    * <p>{@code jsonMode} must be false for any call whose reply is consumed as plain text. JSON mode
    * is not a formatting hint — it forces the model to emit an object, so a prompt asking for prose
    * or for a DSL program comes back wrapped in one ({@code {"program": "..."}}) and the caller feeds
    * the wrapper to a parser that has no idea what to do with it. Only the agent turn, whose contract
    * really is a JSON object, may pass true.
    */
   private static void applyLlmParams(JsonObject requestBody, boolean jsonMode) {
      String model = LlmConfig.model;
      boolean openAi = LlmConfig.baseUrl != null && LlmConfig.baseUrl.contains("api.openai.com");
      // OpenAI's gpt-5.x and o-series lock temperature to the default and 400 on any override;
      // their older gpt-4.x models still honor it, as do llama.cpp/xAI/Anthropic-compat.
      boolean fixedTemperature = openAi && model != null
            && (model.startsWith("gpt-5") || model.matches("^o\\d.*"));
      if (model != null && !model.isBlank()) {
         requestBody.addProperty("model", model);
      }
      if (LlmConfig.temperature >= 0 && !fixedTemperature) {
         requestBody.addProperty("temperature", LlmConfig.temperature);
      }
      if (LlmConfig.maxTokens > 0) {
         // OpenAI retired max_tokens on newer models ("use max_completion_tokens instead") but
         // accepts the new name on ALL its current chat models — so switch on endpoint, not model.
         // Every other OpenAI-compatible backend (llama.cpp, xAI, Anthropic) expects max_tokens.
         requestBody.addProperty(openAi ? "max_completion_tokens" : "max_tokens", LlmConfig.maxTokens);
      }
      if (jsonMode && LlmConfig.useGrammar) {
         // OpenAI-compatible JSON mode: forces the model to emit a JSON object instead of prose.
         // Honored by both xAI/Grok and llama.cpp — stops chatty models replying with bare sentences.
         JsonObject responseFormat = new JsonObject();
         responseFormat.addProperty("type", "json_object");
         requestBody.add("response_format", responseFormat);
      }
   }

   /**
    * Marker on the object returned when the reply could not be parsed as the agent contract.
    *
    * <p>Such a turn carries a message but no command, which means the agent stops acting. Consumers
    * check this so they can queue a follow-up rather than let the plan die mid-sentence — the
    * observed failure was a companion announcing "now building the wheat field" and then standing
    * still forever.
    */
   public static final String FALLBACK_MARKER = "_fallback";

   public JsonObject completeConversation(ConversationHistory conversationHistory) throws Exception {
      JsonObject requestBody = new JsonObject();
      JsonArray messagesArray = new JsonArray();

      for (JsonObject msg : conversationHistory.getListJSON()) {
         messagesArray.add(msg);
      }
      String lastMessageForDebug = conversationHistory.getListJSON().get(conversationHistory.getListJSON().size() - 1)
            .toString();

      requestBody.add("messages", messagesArray);
      applyLlmParams(requestBody, true);

      String content = requestContent(requestBody, lastMessageForDebug);
      try {
         return Utils.parseCleanedJson(content);
      } catch (Exception first) {
         // One bad sample should not cost the turn. JSON mode does NOT prevent this: a bare quoted
         // string is valid JSON, just not an object, so `response_format: json_object` is satisfied
         // and the parse still fails. Sampling is non-deterministic, so asking again usually gets a
         // well-formed object. The retry counts against llm.maxRequests, which is correct.
         boolean firstTruncated = lastReplyTruncated;
         if (firstTruncated) {
            LOGGER.warn("LLM reply was cut off by the output token limit (llm.maxTokens={}); retrying once. Raw=<<{}>>",
                  LlmConfig.maxTokens, content);
         } else {
            LOGGER.warn("LLM response was not the expected JSON object ({}); retrying once. Raw=<<{}>>",
                  first.getMessage(), content);
         }
         String retried = requestContent(requestBody, lastMessageForDebug);
         try {
            return Utils.parseCleanedJson(retried);
         } catch (Exception second) {
            boolean truncated = lastReplyTruncated;
            if (truncated) {
               // Retrying cannot help: the cap is the same, so the second reply is cut off in the
               // same place. Say so once, plainly, rather than reporting it as malformed JSON.
               LOGGER.error("LLM reply was cut off by the output token limit again (llm.maxTokens={}). "
                           + "Nothing ran. Raise llm.maxTokens to at least {}. Raw=<<{}>>",
                     LlmConfig.maxTokens, LlmConfig.MIN_USEFUL_MAX_TOKENS, retried);
            } else {
               LOGGER.error("LLM response was not JSON after a retry ({}). Treating as plain message. Raw=<<{}>>",
                     second.getMessage(), retried);
            }
            JsonObject fallback = new JsonObject();
            fallback.addProperty("reason", "");
            fallback.addProperty("command", "");
            fallback.addProperty("message", speakableFallback(retried));
            fallback.addProperty(FALLBACK_MARKER, true);
            if (truncated) {
               fallback.addProperty(TRUNCATED_MARKER, true);
            }
            return fallback;
         }
      }
   }

   /** One request/response round trip, returning the raw assistant content. */
   private String requestContent(JsonObject requestBody, String lastMessageForDebug) throws Exception {
      enforceRequestCap();
      LOGGER.info("Called complete conversation (string) HTTP request, last msg={}", lastMessageForDebug);
      Map<String, JsonElement> responseMap = Player2HTTPUtils.sendRequest(controller.getOwner(), clientId,
            "/v1/chat/completions", true, requestBody);
      recordUsage(responseMap);
      if (responseMap.containsKey("choices")) {
         JsonArray choices = responseMap.get("choices").getAsJsonArray();
         if (choices.size() != 0) {
            JsonObject messageObject = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (messageObject != null && messageObject.has("content")) {
               LOGGER.info("Finished complete conversation HTTP request last msg={}", lastMessageForDebug);
               lastReplyTruncated = wasTruncated(choices);
               return messageObject.get("content").getAsString();
            }
         }
      }
      throw new Exception("Invalid response format: " + responseMap.toString());
   }

   /**
    * Whether the model ran out of output budget mid-reply, per {@code finish_reason}.
    *
    * <p>Worth reading rather than inferring from the parse failure: a truncated reply is well-formed
    * JSON right up to the cut, so it surfaces only as {@code Unterminated string at … path $.command}
    * and looks identical to a model that simply cannot produce JSON. The two need opposite fixes —
    * one is a config value, the other is a model choice — and telling the agent it emitted bad JSON
    * when it was cut off makes it re-send the same over-long reply and get cut off again.
    */
   private static boolean wasTruncated(JsonArray choices) {
      JsonElement reason = choices.get(0).getAsJsonObject().get("finish_reason");
      return reason != null && !reason.isJsonNull() && "length".equals(reason.getAsString());
   }

   /**
    * Set by the most recent {@link #requestContent} call. Read only while that call's content is
    * being parsed, on the same thread, so the single field is sufficient.
    */
   private boolean lastReplyTruncated = false;

   /** Marks a fallback caused by the output token cap rather than by an unparseable model. */
   public static final String TRUNCATED_MARKER = "_truncated";

   /**
    * Turns an unparseable reply into something safe to say out loud, or "" to stay quiet.
    *
    * <p>The raw content used to be spoken verbatim, which meant a thinking model's {@code <think>}
    * monologue — and any half-written JSON around it — was broadcast to chat. Reasoning is stripped
    * first; whatever is left is only spoken if it still reads like a sentence rather than machine
    * output. Silence is better than narrating the model's internals at the player.
    */
   static String speakableFallback(String content) {
      // A lone JSON string is the common shape here — unwrap it so the quote marks are not spoken.
      String unwrapped = Utils.unwrapJsonString(content);
      String text = Utils.stripReasoning(unwrapped != null ? unwrapped : content).trim();
      if (text.isEmpty()) {
         return "";
      }
      // Any leftover structure (braces, quoted keys, tags) means this was a malformed response
      // object, not speech. Saying it would leak the protocol into the world.
      if (text.contains("{") || text.contains("}") || text.contains("\"command\"")
            || text.contains("\"reason\"") || text.contains("\"message\"") || text.matches("(?s).*<[^>]+>.*")) {
         LOGGER.warn("Discarding unparseable reply that looks like machine output rather than speech");
         return "";
      }
      // The prompt asks for under 250 characters; a runaway reply is a malfunction, not dialogue.
      return text.length() > 250 ? text.substring(0, 250).trim() : text;
   }

   public String completeConversationToString(ConversationHistory conversationHistory) throws Exception {
      enforceRequestCap();
      JsonObject requestBody = new JsonObject();
      JsonArray messagesArray = new JsonArray();

      for (JsonObject msg : conversationHistory.getListJSON()) {
         messagesArray.add(msg);
      }

      requestBody.add("messages", messagesArray);
      // Plain text out: the DSL codegen and the memory summarizer both parse/store this raw.
      applyLlmParams(requestBody, false);
      String lastMessageForDebug = conversationHistory.getListJSON().get(conversationHistory.getListJSON().size() - 1)
            .toString();
      LOGGER.info("Called complete conversation (string) HTTP request, last msg={}", lastMessageForDebug);
      Map<String, JsonElement> responseMap = Player2HTTPUtils.sendRequest(controller.getOwner(), clientId,
            "/v1/chat/completions", true, requestBody);
      recordUsage(responseMap);
      if (responseMap.containsKey("choices")) {
         JsonArray choices = responseMap.get("choices").getAsJsonArray();
         if (choices.size() != 0) {
            JsonObject messageObject = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (messageObject != null && messageObject.has("content")) {
               LOGGER.info("Finished complete conversation HTTP request last msg={}", lastMessageForDebug);
               if (wasTruncated(choices)) {
                  // No JSON parse to fail here — the caller reads this as build DSL or as a memory
                  // summary — so a cut-off reply would otherwise go through as a half-written plan
                  // with nothing anywhere saying why it was wrong.
                  LOGGER.warn("Plain-text LLM reply was cut off by the output token limit "
                        + "(llm.maxTokens={}); the result is incomplete. Raise it to at least {}.",
                        LlmConfig.maxTokens, LlmConfig.MIN_USEFUL_MAX_TOKENS);
               }
               return messageObject.get("content").getAsString();
            }
         }
      }

      throw new Exception("Invalid response format: " + responseMap.toString());
   }

   /**
    * Ask the owner's client to speak {@code message}.
    *
    * <p>Local-TTS path: we send the Kokoro endpoint/voice/speed rather than Player2 credentials, so no
    * cloud auth token is needed (the old {@code awaitToken} call threw in local mode). The client does
    * the synthesis request and playback — see {@code AudioUtils.streamAudio}.
    *
    * <p>{@code onFinish} runs even if the send fails: the caller arms its lock release from it, and a
    * silent failure here used to pin that lock forever.
    */
   public void textToSpeech(String message, Character character, Consumer<Map<String, JsonElement>> onFinish) {
      try {
         // A persona-supplied voice wins over the configured default.
         String voice = TtsConfig.voice;
         String[] ids = character.voiceIds();
         if (ids != null && ids.length > 0 && ids[0] != null && !ids[0].isBlank()) {
            voice = ids[0];
         }

         FriendlyByteBuf buf = PacketByteBufs.create();
         buf.writeUtf(TtsConfig.normalizedEndpoint());
         buf.writeUtf(TtsConfig.model);
         buf.writeUtf(voice);
         buf.writeUtf(message);
         buf.writeDouble(TtsConfig.speed);

         ServerPlayNetworking.send((ServerPlayer) controller.getOwner(),
               new ResourceLocation("playerengine", "stream_tts"), buf);
      } catch (Exception e) {
         System.err.println("[Player2APIService/textToSpeech]: Error" + e.getMessage());
      } finally {
         onFinish.accept(null);
      }
   }

   // public void textToSpeech(String message, Character character,
   // Consumer<Map<String, JsonElement>> onFinish) {
   // try {
   // JsonObject requestBody = new JsonObject();
   // requestBody.addProperty("speed", 1);
   // requestBody.addProperty("text", message);
   // requestBody.addProperty("audio_format", "mp3");
   // JsonArray voiceIdsArray = new JsonArray();
   //
   // for (String voiceId : character.voiceIds()) {
   // voiceIdsArray.add(voiceId);
   // }
   //
   // requestBody.add("voice_ids", voiceIdsArray);
   // LOGGER.info("TTS request w/ msg={}", message);
   // Map<String, JsonElement> responseMap =
   // Player2HTTPUtils.sendRequest(controller.getOwner(), clientId,"/v1/tts/speak",
   // true, requestBody);
   // onFinish.accept(responseMap);
   // } catch (Exception var9) {
   // }
   // }

   public void startSTT() {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("timeout", 180);

      try {
         Player2HTTPUtils.sendRequest(controller.getOwner(), clientId, "/v1/stt/start", true, requestBody);
      } catch (Exception var3) {
         System.err.println("[Player2APIService/startSTT]: Error" + var3.getMessage());
      }
   }

   public String stopSTT() {
      try {
         Map<String, JsonElement> responseMap = Player2HTTPUtils.sendRequest(controller.getOwner(), clientId,
               "/v1/stt/stop", true, null);
         if (!responseMap.containsKey("text")) {
            throw new Exception("Could not find key 'text' in response");
         } else {
            return responseMap.get("text").getAsString();
         }
      } catch (Exception var2) {
         return var2.getMessage();
      }
   }

   public void trySendHeartbeat() {
      if (LlmConfig.localMode) {
         return; // no Player2 cloud heartbeat in local mode
      }
      if (HeartbeatManager.shouldHeartbeat(controller.getOwnerUsername(), clientId)) {
         sendHeartbeat();
         HeartbeatManager.storeHeartbeatTime(controller.getOwnerUsername(), clientId);
      }
   }

   public void sendHeartbeat() {
      try {
         System.out.println("Sending Heartbeat " + clientId);
         Player2HTTPUtils.sendRequest(controller.getOwner(), clientId, "/v1/health", false, null);
         System.out.println("Heartbeat Successful");
      } catch (Exception var2) {
         System.err.printf("Heartbeat Fail: %s", var2.getMessage());
      }
   }
}