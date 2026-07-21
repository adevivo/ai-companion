package com.neovetta.aicompanion.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.CompanionConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * In-game editor for {@code config/aicompanion.json}, built with Cloth Config. Opened via
 * {@code /companion config} (S2C packet → here) or Mod Menu's Configure button.
 *
 * <p>Design: the screen edits the JSON <em>file</em>, not the engine statics. On open it parses the
 * file into a {@link JsonObject}; save consumers write values back into that object; the saving
 * runnable pretty-prints it to disk (preserving the {@code _help}/{@code _usage} doc keys admins
 * see when hand-editing) and then runs {@link CompanionConfig#reloadAndApply} on the integrated
 * server — the same "apply" step as {@code /companion reload}. In singleplayer that makes every
 * non-asterisked field live immediately; name/description/skin stay baked into the spawned entity,
 * hence the asterisk + despawn/spawn note.
 *
 * <p>Not in singleplayer (main menu, or connected to a remote server): the file still saves, but
 * there is no local server to apply it to — it takes effect on the next world load. A guest on a
 * LAN world edits their <em>own</em> file, not the host's; that client/server split is a known
 * pre-1.0 limitation.
 */
public final class CompanionConfigScreen {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Skin dropdown sentinel for "no custom skin" — maps to {@code ""} in the file. */
    private static final String DEFAULT_SKIN = "(default)";

    private CompanionConfigScreen() {}

    public static Screen create(Screen parent) {
        // Ensure the file exists (mod init writes it, but be safe), then edit it in place.
        JsonObject root;
        try {
            Path path = CompanionConfig.configPath();
            if (Files.notExists(path)) {
                CompanionConfig.load();
            }
            root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (Exception e) {
            AiCompanion.LOGGER.warn("[{}] config screen: cannot read config ({}) — editing a fresh default",
                    AiCompanion.MOD_ID, e.toString());
            root = new JsonObject();
        }
        final JsonObject config = root;

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("AI Companion"))
                .setSavingRunnable(() -> save(config));
        ConfigEntryBuilder eb = builder.entryBuilder();

        buildIdentity(builder.getOrCreateCategory(Text.literal("Identity")), eb, config);
        buildLlm(builder.getOrCreateCategory(Text.literal("LLM")), eb, config);
        buildTts(builder.getOrCreateCategory(Text.literal("Voice (TTS)")), eb, config);
        buildBehavior(builder.getOrCreateCategory(Text.literal("Behavior")), eb, config);

        return builder.build();
    }

    // ## Categories

    private static void buildIdentity(ConfigCategory cat, ConfigEntryBuilder eb, JsonObject config) {
        JsonObject companion = section(config, "companion");
        cat.addEntry(eb.startTextDescription(Text.literal(
                        "Fields marked * apply after /companion despawn + /companion spawn. Everything else applies when you save."))
                .build());
        cat.addEntry(eb.startStrField(Text.literal("Name *"), str(companion, "name", "Vetta"))
                .setDefaultValue("Vetta")
                .setTooltip(Text.literal("The companion's name — shown above its head and used as its chat identity."))
                .setSaveConsumer(v -> companion.addProperty("name", v))
                .build());
        cat.addEntry(eb.startStrField(Text.literal("Description *"), str(companion, "description", ""))
                .setDefaultValue("A loyal, level-headed companion who watches your back and speaks plainly.")
                .setTooltip(Text.literal("A short third-person description of who the companion is."))
                .setSaveConsumer(v -> companion.addProperty("description", v))
                .build());
        cat.addEntry(eb.startStrField(Text.literal("System Prompt"), str(companion, "systemPrompt", ""))
                .setDefaultValue("You keep your replies short and spoken, like real dialogue. You are dry, practical, and a little wry, but always on your owner's side.")
                .setTooltip(Text.literal("Personality/style instructions injected into the companion's system prompt. Applies live on save — even to an already-spawned companion."))
                .setSaveConsumer(v -> companion.addProperty("systemPrompt", v))
                .build());

        // Skin: dropdown of PNGs currently in config/aicompanion/skins/. The JSON form is either a
        // plain filename string or { file, slim }; normalize to the object form on save.
        JsonObject skin = skinSection(companion);
        String current = str(skin, "file", "");
        cat.addEntry(eb.startSelector(Text.literal("Skin *"), skinOptions(current), current.isBlank() ? DEFAULT_SKIN : current)
                .setDefaultValue(DEFAULT_SKIN)
                .setTooltip(Text.literal("Pick a 64×64 player-skin PNG. Drop new files into config/aicompanion/skins/ and reopen this screen to see them."))
                .setSaveConsumer(v -> skin.addProperty("file", DEFAULT_SKIN.equals(v) ? "" : String.valueOf(v)))
                .build());
        cat.addEntry(eb.startBooleanToggle(Text.literal("Slim Arms *"), bool(skin, "slim", false))
                .setDefaultValue(false)
                .setTooltip(Text.literal("ON for slim (3px, Alex-style) arm skins, OFF for classic (4px, Steve-style)."))
                .setSaveConsumer(v -> skin.addProperty("slim", v))
                .build());
    }

    private static void buildLlm(ConfigCategory cat, ConfigEntryBuilder eb, JsonObject config) {
        JsonObject llm = section(config, "llm");
        cat.addEntry(eb.startStrField(Text.literal("Endpoint"), str(llm, "endpoint", "http://localhost:3030"))
                .setDefaultValue("http://localhost:3030")
                .setTooltip(Text.literal("OpenAI-compatible base URL — NO trailing slash, NO /v1 (the mod appends /v1/chat/completions). Local llama.cpp: http://localhost:3030. Hosted, e.g. xAI: https://api.x.ai"))
                .setSaveConsumer(v -> llm.addProperty("endpoint", v))
                .build());
        cat.addEntry(eb.startStrField(Text.literal("Model"), str(llm, "model", "local"))
                .setDefaultValue("local")
                .setTooltip(Text.literal("Model name sent with each request. llama.cpp ignores it; hosted APIs need it (prefer a non-reasoning model)."))
                .setSaveConsumer(v -> llm.addProperty("model", v))
                .build());
        cat.addEntry(eb.startDoubleField(Text.literal("Temperature"), dbl(llm, "temperature", 0.7))
                .setDefaultValue(0.7)
                .setTooltip(Text.literal("Sampling temperature (0 = deterministic, higher = more varied). Negative = use the server's default."))
                .setSaveConsumer(v -> llm.addProperty("temperature", v))
                .build());
        cat.addEntry(eb.startIntField(Text.literal("Max Tokens"), intVal(llm, "maxTokens", 200))
                .setDefaultValue(200)
                .setTooltip(Text.literal("Cap on tokens generated per reply. Keep small — replies are spoken dialogue, not essays. Zero or negative = server default."))
                .setSaveConsumer(v -> llm.addProperty("maxTokens", v))
                .build());
        if (envApiKeySet()) {
            cat.addEntry(eb.startTextDescription(Text.literal(
                            "API Key: supplied by the AICOMPANION_LLM_APIKEY environment variable — the value in the file is ignored while that is set."))
                    .build());
        } else {
            cat.addEntry(eb.startStrField(Text.literal("API Key"), str(llm, "apiKey", ""))
                    .setDefaultValue("")
                    .setTooltip(Text.literal("Bearer token for a hosted endpoint. Leave blank for local llama.cpp. Prefer the AICOMPANION_LLM_APIKEY environment variable to keep the secret off disk."))
                    .setSaveConsumer(v -> llm.addProperty("apiKey", v))
                    .build());
        }
        cat.addEntry(eb.startIntField(Text.literal("Max Requests"), intVal(llm, "maxRequests", 0))
                .setDefaultValue(0)
                .setMin(0)
                .setTooltip(Text.literal("Hard per-session cap on LLM requests — the companion goes quiet once hit. 0 = unlimited. A cost guardrail for paid endpoints."))
                .setSaveConsumer(v -> llm.addProperty("maxRequests", v))
                .build());
        cat.addEntry(eb.startLongField(Text.literal("Usage Report Every N Tokens"), longVal(llm, "usageReportEveryTokens", 100000L))
                .setDefaultValue(100000L)
                .setMin(0L)
                .setTooltip(Text.literal("Print a running token-usage total to chat every N tokens. Purely informational, never blocks a reply. 0 = never."))
                .setSaveConsumer(v -> llm.addProperty("usageReportEveryTokens", v))
                .build());
    }

    private static void buildTts(ConfigCategory cat, ConfigEntryBuilder eb, JsonObject config) {
        JsonObject tts = section(config, "tts");
        cat.addEntry(eb.startBooleanToggle(Text.literal("Enabled"), bool(tts, "enabled", false))
                .setDefaultValue(false)
                .setTooltip(Text.literal("Voice output via a local Kokoro server. Start it first: cd tts && docker compose up -d. The CLIENT machine must reach the endpoint."))
                .setSaveConsumer(v -> tts.addProperty("enabled", v))
                .build());
        cat.addEntry(eb.startStrField(Text.literal("Endpoint"), str(tts, "endpoint", "http://localhost:8880"))
                .setDefaultValue("http://localhost:8880")
                .setTooltip(Text.literal("Kokoro (OpenAI-compatible) TTS server base URL."))
                .setSaveConsumer(v -> tts.addProperty("endpoint", v))
                .build());
        cat.addEntry(eb.startStrField(Text.literal("Model"), str(tts, "model", "kokoro"))
                .setDefaultValue("kokoro")
                .setSaveConsumer(v -> tts.addProperty("model", v))
                .build());
        cat.addEntry(eb.startStrField(Text.literal("Voice"), str(tts, "voice", "af_heart"))
                .setDefaultValue("af_heart")
                .setTooltip(Text.literal("Voice id. List available ones: curl http://localhost:8880/v1/audio/voices"))
                .setSaveConsumer(v -> tts.addProperty("voice", v))
                .build());
        cat.addEntry(eb.startDoubleField(Text.literal("Speed"), dbl(tts, "speed", 1.0))
                .setDefaultValue(1.0)
                .setTooltip(Text.literal("Playback speed multiplier (1.0 = normal)."))
                .setSaveConsumer(v -> tts.addProperty("speed", v))
                .build());
    }

    private static void buildBehavior(ConfigCategory cat, ConfigEntryBuilder eb, JsonObject config) {
        JsonObject behavior = section(config, "behavior");
        cat.addEntry(eb.startStrField(Text.literal("Trigger Prefix"), str(behavior, "triggerPrefix", ""))
                .setDefaultValue("")
                .setTooltip(Text.literal("When set (e.g. \"@\"), only chat starting with it reaches the companion. Blank = it hears all nearby chat — fine in singleplayer, costly on a paid endpoint."))
                .setSaveConsumer(v -> behavior.addProperty("triggerPrefix", v))
                .build());
        cat.addEntry(eb.startDoubleField(Text.literal("Think Throttle (seconds)"), dbl(behavior, "thinkThrottleSeconds", 0))
                .setDefaultValue(0.0)
                .setMin(0.0)
                .setTooltip(Text.literal("Minimum seconds between LLM turns; messages inside the window queue into the next turn. 0 = no limit."))
                .setSaveConsumer(v -> behavior.addProperty("thinkThrottleSeconds", v))
                .build());
    }

    // ## Save

    /** Write the edited JSON back to disk, then run the shared reload/apply step on the integrated server. */
    private static void save(JsonObject config) {
        Path path = CompanionConfig.configPath();
        try {
            Files.writeString(path, PRETTY.toJson(config) + System.lineSeparator());
        } catch (IOException e) {
            AiCompanion.LOGGER.warn("[{}] config screen: failed to write {} ({}) — changes NOT saved",
                    AiCompanion.MOD_ID, path, e.toString());
            return;
        }
        MinecraftServer server = MinecraftClient.getInstance().getServer();
        if (server != null) {
            // Singleplayer/LAN host: same process as the server, so apply immediately — on the
            // server thread, since reloadAndApply touches live entities.
            server.execute(() -> {
                int updated = CompanionConfig.reloadAndApply(server);
                AiCompanion.LOGGER.info("[{}] config screen: saved + applied ({} live companion(s) updated)",
                        AiCompanion.MOD_ID, updated);
            });
        } else {
            AiCompanion.LOGGER.info("[{}] config screen: saved {} (no local server running — applies on next world load)",
                    AiCompanion.MOD_ID, path);
        }
    }

    // ## Helpers

    /** Get or create a top-level section object, so save consumers always have a target to write into. */
    private static JsonObject section(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            root.add(key, new JsonObject());
        }
        return root.getAsJsonObject(key);
    }

    /**
     * Get or create {@code companion.skin} as an object. The file format also allows a plain string
     * filename; normalize that to {@code { file, slim }} so the toggle has somewhere to live.
     */
    private static JsonObject skinSection(JsonObject companion) {
        if (companion.has("skin") && companion.get("skin").isJsonPrimitive()) {
            String file = companion.get("skin").getAsString();
            JsonObject obj = new JsonObject();
            obj.addProperty("file", file);
            obj.addProperty("slim", false);
            companion.add("skin", obj);
        }
        return section(companion, "skin");
    }

    /** {@code (default)} + every .png currently in the skins dir (+ the configured one even if missing). */
    private static String[] skinOptions(String current) {
        List<String> options = new ArrayList<>();
        options.add(DEFAULT_SKIN);
        try (Stream<Path> files = Files.list(CompanionConfig.skinsDir())) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase().endsWith(".png"))
                    .sorted()
                    .forEach(options::add);
        } catch (IOException e) {
            // Dir missing/unreadable — dropdown just offers the default (plus the current value below).
        }
        if (!current.isBlank() && !options.contains(current)) {
            options.add(current); // keep a configured-but-deleted skin selectable so saving doesn't clobber it
        }
        return options.toArray(new String[0]);
    }

    private static boolean envApiKeySet() {
        String prop = System.getProperty("aicompanion.llm.apiKey");
        if (prop != null && !prop.isBlank()) {
            return true;
        }
        String env = System.getenv("AICOMPANION_LLM_APIKEY");
        return env != null && !env.isBlank();
    }

    private static String str(JsonObject o, String key, String def) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
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
}
