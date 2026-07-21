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

   private String clientId;
   private AltoClefController controller;

   private static MinecraftServer server;

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
      int max = LlmConfig.maxRequests;
      if (max <= 0) {
         return;
      }
      int n = requestCount.incrementAndGet();
      if (n > max) {
         throw new Exception("LLM request cap reached (" + max
               + "). Raise llm.maxRequests or restart the server to continue (cost guardrail).");
      }
      LOGGER.info("LLM request {}/{} (cost guardrail)", n, max);
   }

   private static void applyLlmParams(JsonObject requestBody) {
      if (LlmConfig.model != null && !LlmConfig.model.isBlank()) {
         requestBody.addProperty("model", LlmConfig.model);
      }
      if (LlmConfig.temperature >= 0) {
         requestBody.addProperty("temperature", LlmConfig.temperature);
      }
      if (LlmConfig.maxTokens > 0) {
         requestBody.addProperty("max_tokens", LlmConfig.maxTokens);
      }
      if (LlmConfig.useGrammar) {
         // OpenAI-compatible JSON mode: forces the model to emit a JSON object instead of prose.
         // Honored by both xAI/Grok and llama.cpp — stops chatty models replying with bare sentences.
         JsonObject responseFormat = new JsonObject();
         responseFormat.addProperty("type", "json_object");
         requestBody.add("response_format", responseFormat);
      }
   }

   public JsonObject completeConversation(ConversationHistory conversationHistory) throws Exception {
      enforceRequestCap();
      JsonObject requestBody = new JsonObject();
      JsonArray messagesArray = new JsonArray();

      for (JsonObject msg : conversationHistory.getListJSON()) {
         messagesArray.add(msg);
      }
      String lastMessageForDebug = conversationHistory.getListJSON().get(conversationHistory.getListJSON().size() - 1)
            .toString();

      requestBody.add("messages", messagesArray);
      applyLlmParams(requestBody);
      LOGGER.info("Called complete conversation (string) HTTP request, last msg={}", lastMessageForDebug);
      Map<String, JsonElement> responseMap = Player2HTTPUtils.sendRequest(controller.getOwner(), clientId,
            "/v1/chat/completions", true, requestBody);
      if (responseMap.containsKey("choices")) {
         JsonArray choices = responseMap.get("choices").getAsJsonArray();
         if (choices.size() != 0) {
            JsonObject messageObject = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (messageObject != null && messageObject.has("content")) {
               String content = messageObject.get("content").getAsString();
               LOGGER.info("Finished complete conversation HTTP request last msg={}", lastMessageForDebug);
               try {
                  return Utils.parseCleanedJson(content);
               } catch (Exception e) {
                  // Some models (esp. chatty frontier ones) occasionally reply with a bare sentence
                  // instead of the required JSON. Rather than dropping the turn, treat the text as the
                  // spoken message with no command so the companion stays responsive. Enable
                  // llm.useGrammar (JSON mode) to prevent this at the source.
                  LOGGER.error("LLM response was not JSON ({}). Treating as plain message. Raw=<<{}>>",
                        e.getMessage(), content);
                  JsonObject fallback = new JsonObject();
                  fallback.addProperty("reason", "");
                  fallback.addProperty("command", "");
                  fallback.addProperty("message", content == null ? "" : content.trim());
                  return fallback;
               }
            }
         }
      }

      throw new Exception("Invalid response format: " + responseMap.toString());
   }

   public String completeConversationToString(ConversationHistory conversationHistory) throws Exception {
      enforceRequestCap();
      JsonObject requestBody = new JsonObject();
      JsonArray messagesArray = new JsonArray();

      for (JsonObject msg : conversationHistory.getListJSON()) {
         messagesArray.add(msg);
      }

      requestBody.add("messages", messagesArray);
      applyLlmParams(requestBody);
      String lastMessageForDebug = conversationHistory.getListJSON().get(conversationHistory.getListJSON().size() - 1)
            .toString();
      LOGGER.info("Called complete conversation (string) HTTP request, last msg={}", lastMessageForDebug);
      Map<String, JsonElement> responseMap = Player2HTTPUtils.sendRequest(controller.getOwner(), clientId,
            "/v1/chat/completions", true, requestBody);
      if (responseMap.containsKey("choices")) {
         JsonArray choices = responseMap.get("choices").getAsJsonArray();
         if (choices.size() != 0) {
            JsonObject messageObject = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (messageObject != null && messageObject.has("content")) {
               LOGGER.info("Finished complete conversation HTTP request last msg={}", lastMessageForDebug);
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