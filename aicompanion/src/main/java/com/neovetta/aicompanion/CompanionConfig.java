package com.neovetta.aicompanion;

import adris.altoclef.player2api.BehaviorConfig;
import adris.altoclef.player2api.Character;
import adris.altoclef.player2api.LlmConfig;
import adris.altoclef.player2api.Prompts;
import adris.altoclef.player2api.TtsConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sysadmin-editable config for the AI companion (Phase 3). Loaded once at mod init from
 * {@code config/aicompanion.json}; if that file is absent a documented default is written so admins
 * have a template to edit.
 *
 * <p>Two destinations:
 * <ul>
 *   <li>{@code llm.*} → pushed into the engine's {@link LlmConfig} (endpoint, model, sampling, timeout).</li>
 *   <li>{@code companion.systemPrompt} → engine's {@link Prompts#persona} (persona block, injected into
 *       the engine-owned hardened scaffold — it does NOT replace the RULES/JSON schema).</li>
 * </ul>
 * {@code companion.{name,description}} are held here and read at spawn time to build the {@code Character}.
 */
public final class CompanionConfig {

    private static final String FILE_NAME = "aicompanion.json";

    // Companion identity (read at spawn).
    private static volatile String name = "Vetta";
    private static volatile String description =
            "A loyal, level-headed Minecraft companion who speaks plainly and watches your back.";

    // Skin: a PNG filename dropped into the skins dir (below); blank = default (Steve). slim = 3px arms.
    private static volatile String skinFile = "";
    private static volatile boolean skinSlim = false;

    private CompanionConfig() {}

    public static String name() { return name; }
    public static String description() { return description; }
    public static String skinFile() { return skinFile; }
    public static boolean skinSlim() { return skinSlim; }

    /** Directory to drop skin PNGs into: {@code config/aicompanion/skins/}. Created on load. */
    public static Path skinsDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("aicompanion").resolve("skins");
    }

    /**
     * The engine-facing identity (LLM name/description) built from current config. Rebuilt rather than
     * persisted, so config stays the single source of truth: editing {@code aicompanion.json} and
     * restarting re-identities existing companions. Used at spawn and when re-attaching a brain to a
     * companion restored from a save.
     */
    public static Character character() {
        String n = name();
        return new Character(n, n, "Hi, I'm " + n + " — your companion.", description(), "", new String[0]);
    }

    /** Read config (writing the default first if missing) and apply it to the engine config statics. */
    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        try {
            if (Files.notExists(path)) {
                Files.writeString(path, DEFAULT_JSON);
                AiCompanion.LOGGER.info("[{}] wrote default config to {}", AiCompanion.MOD_ID, path);
            }
            Files.createDirectories(skinsDir()); // so admins have somewhere to drop skin PNGs
            String raw = Files.readString(path);
            apply(JsonParser.parseString(raw).getAsJsonObject());
            AiCompanion.LOGGER.info(
                    "[{}] config loaded: name='{}', skin='{}', llm.endpoint={}, model={}, maxTokens={}, tts={}. Skins dir: {}",
                    AiCompanion.MOD_ID, name, skinFile.isBlank() ? "(default)" : skinFile, LlmConfig.baseUrl,
                    LlmConfig.model, LlmConfig.maxTokens,
                    TtsConfig.enabled ? TtsConfig.voice + " @ " + TtsConfig.endpoint : "off", skinsDir());
        } catch (Exception e) {
            AiCompanion.LOGGER.warn("[{}] failed to load {} ({}) — using built-in defaults",
                    AiCompanion.MOD_ID, path, e.toString());
        }
    }

    private static void apply(JsonObject root) {
        JsonObject companion = obj(root, "companion");
        if (companion != null) {
            name = str(companion, "name", name);
            description = str(companion, "description", description);
            String sysPrompt = str(companion, "systemPrompt", null);
            if (sysPrompt != null) {
                Prompts.persona = sysPrompt;
            }
            // skin: either a plain filename string, or { "file": "...", "slim": bool }.
            if (companion.has("skin") && !companion.get("skin").isJsonNull()) {
                JsonElement skinEl = companion.get("skin");
                if (skinEl.isJsonPrimitive()) {
                    skinFile = skinEl.getAsString();
                } else if (skinEl.isJsonObject()) {
                    JsonObject skin = skinEl.getAsJsonObject();
                    skinFile = str(skin, "file", "");
                    skinSlim = bool(skin, "slim", false);
                }
            }
        }

        JsonObject llm = obj(root, "llm");
        if (llm != null) {
            LlmConfig.baseUrl = str(llm, "endpoint", LlmConfig.baseUrl);
            LlmConfig.model = str(llm, "model", LlmConfig.model);
            LlmConfig.temperature = dbl(llm, "temperature", LlmConfig.temperature);
            LlmConfig.maxTokens = intVal(llm, "maxTokens", LlmConfig.maxTokens);
            LlmConfig.timeoutMs = intVal(llm, "timeoutMs", LlmConfig.timeoutMs);
            LlmConfig.useGrammar = bool(llm, "useGrammar", LlmConfig.useGrammar);
            LlmConfig.maxRequests = intVal(llm, "maxRequests", LlmConfig.maxRequests);
            LlmConfig.usageReportEveryTokens =
                    longVal(llm, "usageReportEveryTokens", LlmConfig.usageReportEveryTokens);
            // API key: env/sysprop wins (so the secret need not live on disk); config is the fallback.
            if (LlmConfig.apiKey == null || LlmConfig.apiKey.isBlank()) {
                LlmConfig.apiKey = str(llm, "apiKey", "");
            }
        }

        JsonObject behavior = obj(root, "behavior");
        if (behavior != null) {
            BehaviorConfig.triggerPrefix = str(behavior, "triggerPrefix", BehaviorConfig.triggerPrefix);
            BehaviorConfig.thinkThrottleSeconds =
                    dbl(behavior, "thinkThrottleSeconds", BehaviorConfig.thinkThrottleSeconds);
        }

        JsonObject tts = obj(root, "tts");
        if (tts != null) {
            TtsConfig.enabled = bool(tts, "enabled", TtsConfig.enabled);
            TtsConfig.endpoint = str(tts, "endpoint", TtsConfig.endpoint);
            TtsConfig.model = str(tts, "model", TtsConfig.model);
            TtsConfig.voice = str(tts, "voice", TtsConfig.voice);
            TtsConfig.speed = dbl(tts, "speed", TtsConfig.speed);
        }
    }

    // ## JSON helpers (missing/mistyped fields fall back to the passed default)

    private static JsonObject obj(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static String str(JsonObject o, String key, String def) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static double dbl(JsonObject o, String key, double def) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static int intVal(JsonObject o, String key, int def) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static long longVal(JsonObject o, String key, long def) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsBoolean() : def;
        } catch (Exception e) {
            return def;
        }
    }

    /** Default config written when {@code config/aicompanion.json} does not exist. */
    private static final String DEFAULT_JSON = """
            {
              "companion": {
                "name": "Vetta",
                "description": "A loyal, level-headed companion who watches your back and speaks plainly.",
                "systemPrompt": "You keep your replies short and spoken, like real dialogue. You are dry, practical, and a little wry, but always on your owner's side.",
                "skin": { "file": "", "slim": false, "_help": "Drop a 64x64 player-skin PNG into config/aicompanion/skins/ and set 'file' to its name (e.g. vetta.png). Blank = default Steve. slim = 3px (Alex) arms." }
              },
              "llm": {
                "endpoint": "http://localhost:3030",
                "model": "local",
                "temperature": 0.7,
                "maxTokens": 200,
                "timeoutMs": 90000,
                "useGrammar": false,
                "apiKey": "",
                "maxRequests": 0,
                "usageReportEveryTokens": 100000,
                "_usage": "usageReportEveryTokens: print a running token-usage total to chat and the log every N tokens (0 = never). Purely informational — it never blocks a reply. maxRequests is the opposite: a hard per-session request cap that makes the companion stop responding once hit (0 = unlimited, the default). Leave maxRequests at 0 unless you are on a paid endpoint and want a hard stop.",
                "_apiKey": "Leave blank for a local llama.cpp server (no auth). For a paid hosted API, paste the key here — or better, leave this blank and set the AICOMPANION_LLM_APIKEY environment variable so the secret never lands on disk. The env var wins if both are set.",
                "_frontier": "To use a hosted OpenAI-compatible model instead of a local one, e.g. xAI/Grok: { \\"endpoint\\": \\"https://api.x.ai\\", \\"model\\": \\"grok-4-1-fast-non-reasoning\\", \\"apiKey\\": \\"xai-...\\" }. NOTE: endpoint is the base URL with NO trailing slash and NO /v1 — the mod appends /v1/chat/completions itself. Pick a non-reasoning model: reasoning models are slower and burn tokens on thinking the companion never uses."
              },
              "tts": {
                "enabled": false,
                "endpoint": "http://localhost:8880",
                "model": "kokoro",
                "voice": "af_heart",
                "speed": 1.0,
                "_help": "Local voice output via Kokoro. Start the stack first: 'cd tts && docker compose up -d', then set enabled=true. The MINECRAFT CLIENT calls this endpoint (the server only sends it the text), so it must be reachable from the client machine. Voices: curl http://localhost:8880/v1/audio/voices. Only the companion's spoken 'message' is voiced — never commands or reasoning."
              },
              "behavior": {
                "triggerPrefix": "",
                "thinkThrottleSeconds": 0,
                "_help": "triggerPrefix: when set (e.g. \\"@\\"), only chat starting with it reaches the companion, and the prefix is stripped before the model sees it. Blank (default) = it answers all nearby chat, which is what you want in singleplayer. Set it on a paid endpoint or a shared world so ambient chatter costs nothing. thinkThrottleSeconds: minimum seconds between LLM turns (0 = no limit). Messages arriving inside the window are queued, not dropped — they fold into the next turn."
              }
            }
            """;
}
