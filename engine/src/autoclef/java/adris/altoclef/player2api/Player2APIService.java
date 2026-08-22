package adris.altoclef.player2api;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.auth.AuthKey;
import adris.altoclef.player2api.auth.AuthenticationManager;
import adris.altoclef.player2api.manager.HeartbeatManager;
import adris.altoclef.player2api.manager.TTSManager;
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
   /**
    * Input tokens the provider served from its prompt cache, when it says so.
    *
    * <p>Tracked because the cost of this mod is dominated by a system prompt that is byte-identical
    * on every request — measured 2026-08-19 at 14,684 chars, 91% of everything sent — and providers
    * bill a repeated prefix at a steep discount (xAI: $0.50/M cached against $2.00/M). Without this
    * number the total alone cannot distinguish "the prefix is being cached" from "it is being paid
    * for in full every turn", and those differ by roughly a factor of three on the bill.
    */
   private static final java.util.concurrent.atomic.AtomicLong cachedPromptTokens =
         new java.util.concurrent.atomic.AtomicLong(0);
   /** Highest {@code totalTokens / usageReportEveryTokens} milestone already reported. */
   private static final java.util.concurrent.atomic.AtomicLong reportedMilestone =
         new java.util.concurrent.atomic.AtomicLong(0);

   /**
    * xAI's cache-routing header. Prompt cache entries live <b>per server</b>, so without this a
    * request goes wherever the balancer sends it and a hit is luck — which is why a byte-identical
    * 14.7k system prompt was still being billed at the uncached rate. The value is opaque to the
    * provider; all it has to be is stable for the conversation it names.
    *
    * <p>Harmless everywhere else: llama.cpp and the Player2 API ignore headers they do not know.
    */
   private static final String CONV_ID_HEADER = "x-grok-conv-id";

   private String clientId;
   private AltoClefController controller;

   /**
    * Stable conversation id for cache routing, resolved once and kept.
    *
    * <p>Derived from the companion's own entity id so two companions do not share a route: their
    * personas differ, so their prompt prefixes differ, and pinning both to one server would have
    * them evicting each other's cache. Resolved lazily because the entity is not necessarily
    * attached when this service is constructed, and it falls back to a random id rather than
    * failing — an unroutable request still works, it just pays full price.
    */
   private volatile String convId;

   public Player2APIService(AltoClefController controller, String clientId) {
      this.clientId = clientId;
      this.controller = controller;
   }

   /**
    * A service with no companion behind it, for a client doing its own thinking.
    *
    * <p>Reuses this class rather than growing a second client that would drift: the retry, salvage
    * and truncation handling here is scar tissue from observed model failures, and a parallel
    * implementation would have to relearn all of it. Only the chat-completion path is supported —
    * TTS, STT and health all genuinely need a companion and its owner.
    *
    * <p>⚠️ Requires {@link LlmConfig#localMode}. The hosted Player2 path authenticates per player
    * against a token the server holds; there is no client-side equivalent.
    */
   public Player2APIService(String clientId) {
      this(null, clientId);
   }

   private String conversationId() {
      String id = convId;
      if (id != null) {
         return id;
      }
      synchronized (this) {
         if (convId == null) {
            String derived = null;
            try {
               if (controller != null && controller.getPlayer() != null) {
                  derived = controller.getPlayer().getUUID().toString();
               }
            } catch (Throwable ignored) {
               // Never let cache routing be the thing that breaks a conversation.
            }
            convId = "aicompanion-" + (derived != null ? derived : UUID.randomUUID().toString());
         }
         return convId;
      }
   }

   /**
    * One chat-completion round trip, with cache routing and usage accounting applied.
    *
    * <p>Every LLM call in this class goes through here so that neither can be forgotten at a new
    * call site — the header is worthless if only two of the three paths send it, since the third
    * would keep landing on other servers and evicting nothing useful.
    */
   private Map<String, JsonElement> chatCompletion(JsonObject requestBody) throws Exception {
      Map<String, JsonElement> responseMap = Player2HTTPUtils.sendRequest(
            controller == null ? null : controller.getOwner(), clientId,
            "/v1/chat/completions", true, requestBody,
            java.util.Collections.singletonMap(CONV_ID_HEADER, conversationId()));
      recordUsage(responseMap);
      return responseMap;
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
      cachedPromptTokens.set(0);
      reportedMilestone.set(0);
   }

   /** Immutable view of this session's LLM spend. */
   public record UsageSnapshot(long promptTokens, long completionTokens, long totalTokens,
         long cachedPromptTokens, int requests) {}

   /**
    * Read the session counters for display. The four values are read independently, so a snapshot
    * taken mid-{@link #recordUsage} can show a total that lags its parts by one request — acceptable
    * for a HUD that refreshes every second, and not worth a lock on the LLM path.
    */
   public static UsageSnapshot usageSnapshot() {
      return new UsageSnapshot(promptTokens.get(), completionTokens.get(), totalTokens.get(),
            cachedPromptTokens.get(), requestCount.get());
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
         long cached = cachedPromptTokensOf(usage);

         cachedPromptTokens.addAndGet(cached);
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

   /**
    * Prompt tokens served from cache, read from whichever shape the provider uses.
    *
    * <p>Two spellings on purpose. xAI names it {@code cached_prompt_text_tokens} at the top of the
    * usage object; the OpenAI-compatible convention nests {@code cached_tokens} under
    * {@code prompt_tokens_details}, and several proxies follow that instead. Reading only one would
    * report a flat zero against a provider that was in fact caching perfectly, which is worse than
    * not reporting at all — it is a number that argues for changes nobody needs to make.
    */
   private static long cachedPromptTokensOf(JsonObject usage) {
      if (usage.has("cached_prompt_text_tokens")) {
         return usage.get("cached_prompt_text_tokens").getAsLong();
      }
      JsonElement details = usage.get("prompt_tokens_details");
      if (details != null && details.isJsonObject()) {
         JsonObject d = details.getAsJsonObject();
         if (d.has("cached_tokens")) {
            return d.get("cached_tokens").getAsLong();
         }
      }
      return 0;
   }

   private void reportUsage(long runningTotal) {
      long in = promptTokens.get();
      long cached = cachedPromptTokens.get();
      // The cache share is the actionable half of this line: input is the overwhelming majority of
      // spend here, and a low percentage against a prompt whose prefix never changes means the
      // discount is being missed rather than that there is nothing to cache.
      String cacheNote = cached > 0
            ? String.format(" %,d of the input was cached (%.0f%%).", cached, 100.0 * cached / Math.max(1, in))
            : " None of the input was reported as cached.";
      String summary = String.format(
            "LLM usage this session: %,d tokens (%,d in / %,d out) over %,d requests.%s",
            runningTotal, in, completionTokens.get(), requestCount.get(), cacheNote);
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
      applyLlmParams(requestBody, jsonMode, null);
   }

   /**
    * @param temperatureOverride a temperature for this call alone, or null to use
    *        {@link LlmConfig#temperature}. For side tasks that want determinism rather than
    *        conversational variety. An endpoint that locks temperature still wins — overriding it there
    *        is a 400, not a warmer reply.
    */
   private static void applyLlmParams(JsonObject requestBody, boolean jsonMode,
         Double temperatureOverride) {
      String model = LlmConfig.model;
      boolean openAi = LlmConfig.baseUrl != null && LlmConfig.baseUrl.contains("api.openai.com");
      // OpenAI's gpt-5.x and o-series lock temperature to the default and 400 on any override;
      // their older gpt-4.x models still honor it, as do llama.cpp/xAI/Anthropic-compat.
      boolean fixedTemperature = openAi && model != null
            && (model.startsWith("gpt-5") || model.matches("^o\\d.*"));
      if (model != null && !model.isBlank()) {
         requestBody.addProperty("model", model);
      }
      double temperature = temperatureOverride == null ? LlmConfig.temperature : temperatureOverride;
      if (temperature >= 0 && !fixedTemperature) {
         requestBody.addProperty("temperature", temperature);
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
    * Why a reply hit the token cap, and therefore whether raising the cap is the fix.
    *
    * <p>⚠️ "Raise llm.maxTokens" is the right advice for a reply that was genuinely too long, and the
    * <b>wrong</b> advice for a model that produced its answer and then failed to stop. Observed
    * 2026-08-22 on a free 9B model: it emitted a complete, correct {@code {reason, command, message}}
    * and then several hundred blank lines followed by {@code </</</</…} until the cap ended it. The
    * closing brace never arrived, so the object could not be parsed. Raising the cap there does not
    * rescue the turn — it buys more garbage, and on a paid endpoint it is billed.
    *
    * <p>The tell is cheap and reliable: a long run of one repeated character at the tail. Small and
    * heavily-quantised models are where this shows up, which is exactly what a free tier is made of.
    */
   private static String truncationNote(String content) {
      if (looksDegenerate(content)) {
         return "The tail is a repeated character, so the model did not run out of room — it failed to "
               + "STOP. Raising llm.maxTokens buys more of the same (and costs more on a paid "
               + "endpoint); use a larger or less quantised model instead.";
      }
      return "Raise llm.maxTokens to at least " + LlmConfig.MIN_USEFUL_MAX_TOKENS + ".";
   }

   /** A tail of one character repeated far past anything a real reply ends with. */
   private static boolean looksDegenerate(String content) {
      if (content == null) {
         return false;
      }
      String tail = content.stripTrailing();
      // Trailing whitespace is itself the commonest form of it, so measure before stripping too.
      int trailingBlank = content.length() - tail.length();
      if (trailingBlank >= 200) {
         return true;
      }
      if (tail.isEmpty()) {
         return false;
      }
      // Otherwise look for one character repeated at the end, ignoring whitespace between repeats.
      String squeezed = tail.replaceAll("\s+", "");
      if (squeezed.length() < 60) {
         return false;
      }
      char last = squeezed.charAt(squeezed.length() - 1);
      int run = 0;
      for (int i = squeezed.length() - 1; i >= 0 && squeezed.charAt(i) == last; i--) {
         run++;
      }
      if (run >= 40) {
         return true;
      }
      // A repeated short motif rather than a single char — "</</</" is two characters, not one.
      String motif = squeezed.substring(Math.max(0, squeezed.length() - 2));
      int motifRun = 0;
      for (int i = squeezed.length() - 2; i >= 0 && squeezed.startsWith(motif, i); i -= 2) {
         motifRun++;
      }
      return motifRun >= 20;
   }

   /**
    * Whether this call actually asked for JSON, said on the failure that cannot otherwise tell you.
    *
    * <p>"The model is bad at JSON" and "nothing ever asked it for JSON" produce an identical log line
    * and want opposite fixes — one is a model or endpoint to replace, the other is a setting to turn
    * back on. Worse, an endpoint that silently ignores {@code response_format} looks exactly like a
    * model with a weak grasp of the envelope: it obeys the system prompt most turns and drifts into
    * prose on the rest, which is the intermittent pattern that reads as "flaky model" and is not.
    *
    * <p>Constrained decoding cannot produce prose. So if this says JSON mode was requested and the
    * reply is still prose, the endpoint is not honouring the field, and no amount of prompt work will
    * fix it — llama.cpp and xAI honour it, several local OpenAI-compatible shims do not.
    */
   private static String jsonModeNote() {
      return LlmConfig.useGrammar
            ? "JSON mode WAS requested (response_format=json_object), so this endpoint is ignoring it "
                  + "— a backend that honours it cannot return prose."
            : "JSON mode is OFF (llm.useGrammar=false), so nothing asked the model for JSON — turn it "
                  + "on before blaming the model.";
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

      for (JsonObject msg : conversationHistory.getListJSONBounded(LlmConfig.maxPromptChars)) {
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
            LOGGER.warn("LLM reply was cut off by the output token limit (llm.maxTokens={}); retrying once. {} Raw=<<{}>>",
                  LlmConfig.maxTokens, truncationNote(content), content);
         } else {
            LOGGER.warn("LLM response was not the expected JSON object ({}); retrying once. {} Raw=<<{}>>",
                  first.getMessage(), jsonModeNote(), content);
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
                           + "Nothing ran. {} Raw=<<{}>>",
                     LlmConfig.maxTokens, truncationNote(retried), retried);
            } else {
               LOGGER.error("LLM response was not JSON after a retry ({}). Treating as plain message. "
                           + "{} Raw=<<{}>>",
                     second.getMessage(), jsonModeNote(), retried);
            }
            // Before giving up: a model that ignores the JSON envelope usually still answers the
            // right shape, just rendered as prose — "**Command:** `get coal_ore 29`". Throwing that
            // away turns a correct decision into a companion that talks and stands still, which is
            // indistinguishable from a broken brain. Recover the fields if they are legible.
            JsonObject salvaged = salvageLabelledReply(retried);
            if (salvaged != null) {
               LOGGER.warn("Recovered a prose reply that was not JSON: command={}",
                     salvaged.get("command").getAsString());
               return salvaged;
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
      Map<String, JsonElement> responseMap = chatCompletion(requestBody);
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
   /**
    * Field labels as models render them when they answer in prose instead of the JSON envelope:
    * {@code **Command:**}, {@code ## Command}, {@code Command:} — optionally decorated, always
    * followed by a colon. Each capture runs to the next label or the end of the reply.
    */
   private static final java.util.regex.Pattern LABELLED_FIELD = java.util.regex.Pattern.compile(
         "(?is)(?:^|\\n)[\\s>#*_`-]*(reason|command|message)[\\s*_`]*:\\s*"
               + "(.*?)(?=\\n[\\s>#*_`-]*(?:reason|command|message)[\\s*_`]*:|\\z)");

   /**
    * Rebuild the agent contract from a labelled prose reply, or null if it is not legible.
    *
    * <p>Deliberately conservative: a command is only accepted if it survives as a single short line
    * once decoration is stripped. Anything sprawling is prose that happened to contain the word
    * "command", and inventing a command from it would be worse than dropping the turn.
    */
   static JsonObject salvageLabelledReply(String content) {
      if (content == null || content.isBlank()) {
         return null;
      }
      java.util.regex.Matcher m = LABELLED_FIELD.matcher(content);
      String reason = null;
      String command = null;
      String message = null;
      while (m.find()) {
         String value = m.group(2) == null ? "" : m.group(2).trim();
         switch (m.group(1).toLowerCase(java.util.Locale.ROOT)) {
            case "reason" -> reason = value;
            case "command" -> command = value;
            case "message" -> message = value;
            default -> { }
         }
      }
      if (command == null && message == null) {
         return null; // not a labelled reply at all
      }

      String cleanedCommand = cleanSalvagedCommand(command);
      String cleanedMessage = speakableFallback(stripDecoration(message == null ? "" : message));
      if (cleanedCommand.isEmpty() && cleanedMessage.isEmpty()) {
         return null;
      }

      JsonObject out = new JsonObject();
      out.addProperty("reason", stripDecoration(reason == null ? "" : reason));
      out.addProperty("command", cleanedCommand);
      out.addProperty("message", cleanedMessage);
      return out;
   }

   /** First line only, decoration removed, and rejected outright if it does not look like a command. */
   private static String cleanSalvagedCommand(String raw) {
      if (raw == null) {
         return "";
      }
      String line = stripDecoration(raw).lines().findFirst().orElse("").trim();
      // An empty command is a legitimate answer ("say something, do nothing"), so it is not a failure
      // — but a sentence is. Real commands are `verb arg arg`, short and unpunctuated.
      if (line.isEmpty() || line.length() > 80 || !line.matches("[A-Za-z_][A-Za-z0-9_\\- .]*")) {
         return "";
      }
      return line;
   }

   /**
    * Strip the backticks, asterisks and quotes Markdown adds.
    *
    * <p>Underscores are deliberately left alone: they are Markdown emphasis, but they are also load
    * bearing in every Minecraft identifier, and stripping them turns {@code get coal_ore 29} into
    * {@code get coalore 29} — a command that parses and then fails on an item that does not exist.
    */
   private static String stripDecoration(String raw) {
      return raw.replaceAll("[`*]+", "").replaceAll("^[\"'\\s]+|[\"'\\s]+$", "").trim();
   }

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

   /**
    * One deterministic JSON call for a side task, returning the raw assistant content unparsed.
    *
    * <p>Distinct from {@link #completeConversation} in what it does on failure: that method exists to
    * protect a conversation turn, so it retries, salvages prose and finally fabricates a
    * reason/command/message object rather than let the companion go mute. None of that is wanted here.
    * A side task that cannot be parsed should simply produce nothing, and inventing an agent-contract
    * object for a caller that is not the agent would be worse than an empty string.
    *
    * <p>Temperature 0 and JSON mode, because those are the conditions the extraction numbers were
    * measured under: 970 turns produced 0 parse failures at those settings, and running with the
    * conversational temperature instead would quietly be a different experiment from the one whose
    * results justified building this.
    *
    * <p>Counts against {@link LlmConfig#maxRequests} like any other call — a per-turn side task is
    * exactly the thing a spend cap exists to bound.
    */
   public String completeDeterministicJson(ConversationHistory conversationHistory) throws Exception {
      enforceRequestCap();
      JsonObject requestBody = new JsonObject();
      JsonArray messagesArray = new JsonArray();
      for (JsonObject msg : conversationHistory.getListJSONBounded(LlmConfig.maxPromptChars)) {
         messagesArray.add(msg);
      }
      requestBody.add("messages", messagesArray);
      applyLlmParams(requestBody, true, 0.0);
      Map<String, JsonElement> responseMap = chatCompletion(requestBody);
      if (responseMap.containsKey("choices")) {
         JsonArray choices = responseMap.get("choices").getAsJsonArray();
         if (choices.size() != 0) {
            JsonObject messageObject = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (messageObject != null && messageObject.has("content")) {
               if (wasTruncated(choices)) {
                  LOGGER.warn("Deterministic JSON reply was cut off by the output token limit "
                        + "(llm.maxTokens={}); it will not parse. Raise it to at least {}.",
                        LlmConfig.maxTokens, LlmConfig.MIN_USEFUL_MAX_TOKENS);
               }
               return messageObject.get("content").getAsString();
            }
         }
      }
      LOGGER.warn("Deterministic JSON call returned no choices; treating as no result.");
      return "";
   }

   public String completeConversationToString(ConversationHistory conversationHistory) throws Exception {
      enforceRequestCap();
      JsonObject requestBody = new JsonObject();
      JsonArray messagesArray = new JsonArray();

      for (JsonObject msg : conversationHistory.getListJSONBounded(LlmConfig.maxPromptChars)) {
         messagesArray.add(msg);
      }

      requestBody.add("messages", messagesArray);
      // Plain text out: the DSL codegen and the memory summarizer both parse/store this raw.
      applyLlmParams(requestBody, false);
      String lastMessageForDebug = conversationHistory.getListJSON().get(conversationHistory.getListJSON().size() - 1)
            .toString();
      LOGGER.info("Called complete conversation (string) HTTP request, last msg={}", lastMessageForDebug);
      Map<String, JsonElement> responseMap = chatCompletion(requestBody);
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
    * <p>Local-TTS path: we send the Kokoro voice/speed rather than Player2 credentials, so no cloud
    * auth token is needed (the old {@code awaitToken} call threw in local mode). The client does the
    * synthesis request and playback — see {@code AudioUtils.streamAudio} — and answers on
    * {@link TTSManager#ACK_CHANNEL} when the line is finished or could not be played.
    *
    * <p>The endpoint still goes out on the wire, but the client uses its OWN {@code tts.endpoint}
    * and falls back to this only if that is blank. This value describes THIS machine's network,
    * which on a dedicated server is not the network the audio has to be fetched over.
    *
    * @param speaker the companion entity speaking, echoed back in the ack so the right speech lock is
    *                released
    * @return whether the request actually went out. False means nothing will speak and no ack is
    *         coming, so the caller must not wait on one.
    */
   public boolean textToSpeech(String message, Character character, UUID speaker) {
      try {
         if (!(controller.getOwner() instanceof ServerPlayer owner)) {
            return false;
         }
         // This client has already told us it has nowhere to play audio. Skipping the send is what
         // makes voice safe to leave on by default: an unequipped machine pays nothing per line.
         if (TTSManager.isTtsUnavailable(owner.getUUID())) {
            return false;
         }

         // A persona-supplied voice wins over the configured default.
         String voice = TtsConfig.voice;
         String[] ids = character.voiceIds();
         if (ids != null && ids.length > 0 && ids[0] != null && !ids[0].isBlank()) {
            voice = ids[0];
         }

         FriendlyByteBuf buf = PacketByteBufs.create();
         buf.writeUUID(speaker);
         buf.writeUtf(TtsConfig.normalizedEndpoint());
         buf.writeUtf(TtsConfig.model);
         buf.writeUtf(voice);
         buf.writeUtf(message);
         buf.writeDouble(TtsConfig.speed);

         ServerPlayNetworking.send(owner, TTSManager.SPEAK_CHANNEL, buf);
         return true;
      } catch (Exception e) {
         System.err.println("[Player2APIService/textToSpeech]: Error" + e.getMessage());
         return false;
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