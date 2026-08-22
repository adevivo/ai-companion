package com.neovetta.aicompanion.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.CompanionConfig;
import com.neovetta.aicompanion.CompanionSkills;
import com.neovetta.aicompanion.entity.CompanionEntity;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.DropdownBoxEntry;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
 * <p>Connected to a remote server there is no local server to walk, so save runs
 * {@link CompanionConfig#reloadClientOwned} instead: the endpoints this machine calls — the brain,
 * embeddings, and the Kokoro server audio is fetched from — go live immediately, while anything the
 * server owns (caps, permissions, chat routing) still comes from the server's own file. A guest on
 * a LAN world edits their <em>own</em> file, not the host's; that client/server split is a known
 * pre-1.0 limitation.
 */
public final class CompanionConfigScreen {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Skin dropdown sentinel for "no custom skin" — maps to {@code ""} in the file. */
    private static final String DEFAULT_SKIN = "(default)";

    /** Shown when a companion sets no voice of its own, i.e. it falls back to the global one. */
    private static final String DEFAULT_VOICE = "(use global tts.voice)";

    /**
     * Kokoro voice suggestions for the per-companion picker. The prefix encodes accent and gender:
     * {@code af_}/{@code am_} American female/male, {@code bf_}/{@code bm_} British female/male.
     * The stack serves 68 in total including other languages — {@code GET /v1/audio/voices} lists
     * them all and the box takes free text, so a name missing here is still usable.
     *
     * <p>Ordered with the five the project's own {@code tts/README.md} recommends first, then the
     * rest of the English set, so the top of the list is the tested ground.
     */
    private static final List<String> VOICE_SUGGESTIONS = List.of(
            // README's starting points
            "af_heart", "af_bella", "af_sky", "am_michael", "bm_george",
            // remaining American female / male
            "af_alloy", "af_aoede", "af_jessica", "af_kore", "af_nicole", "af_nova", "af_river",
            "af_sarah",
            "am_adam", "am_echo", "am_eric", "am_fenrir", "am_liam", "am_onyx", "am_puck",
            // British female / male
            "bf_alice", "bf_emma", "bf_isabella", "bf_lily",
            "bm_daniel", "bm_fable", "bm_lewis");

    /**
     * Endpoint suggestions for the LLM combobox — OpenAI-compatible base URLs, WITHOUT {@code /v1}
     * because the engine appends {@code /v1/chat/completions}. Gemini is deliberately absent: its
     * compat path is {@code /v1beta/openai/chat/completions}, which no base URL can produce under
     * that convention.
     */
    private static final List<String> LLM_ENDPOINT_SUGGESTIONS = List.of(
            "http://localhost:3030",        // local llama.cpp
            "https://api.openai.com",       // OpenAI
            "https://api.anthropic.com",    // Anthropic (OpenAI-compat layer)
            "https://api.mistral.ai",       // Mistral
            "https://api.groq.com/openai",  // Groq
            "https://openrouter.ai/api",    // OpenRouter — one key, many models, and a free tier
            "https://api.x.ai");            // xAI

    /**
     * Per-provider model suggestions, keyed by endpoint base URL. The Model combobox shows the
     * saved endpoint's provider first, then every other provider's models — the dropdown's built-in
     * fuzzy search narrows as the user types, so all options stay reachable regardless of which
     * endpoint is saved (Cloth Config snapshots selections at build time; only the ORDER refreshes
     * on save + reopen). Cheapest/recommended first within each provider. Model names churn every
     * few months — verified against provider docs + the user's live /v1/models export 2026-07-22;
     * free text always works, so a stale list only degrades to a missing suggestion.
     */
    private static final java.util.Map<String, List<String>> LLM_MODEL_SUGGESTIONS = java.util.Map.of(
            "http://localhost:3030", List.of("local"),  // llama.cpp ignores the model name
            "https://api.openai.com", List.of(          // $/1M in/out as of 2026-07, cheapest first;
                    "gpt-4.1-nano",     // $0.10/$0.40 — cheapest chat model OpenAI serves
                    "gpt-4o-mini",      // $0.15/$0.60
                    "gpt-5.4-nano",     // $0.20/$1.25
                    "gpt-4.1-mini",     // $0.40/$1.60
                    "gpt-5.4-mini",     // $0.75/$4.50
                    "gpt-5.6-luna",     // $1/$6 — current-gen cost-optimized
                    "gpt-4.1",          // $2/$8 — older gen, solid non-reasoning chat
                    "gpt-5.6-sol"),     // $5/$30 — flagship
            "https://api.anthropic.com", List.of(
                    "claude-haiku-4-5",   // $1/$5 — Anthropic's cheapest tier (no cheaper legacy exists)
                    "claude-sonnet-5",    // $3/$15
                    "claude-sonnet-4-6",  // $3/$15 — previous gen, still active
                    "claude-sonnet-4-5",  // $3/$15 — older gen, still active
                    "claude-opus-4-8"),   // $5/$25 — flagship
            "https://api.x.ai", List.of(    // reasoning variants deliberately excluded — spoken
                    "grok-4-1-fast-non-reasoning",  // dialogue wants fast, non-CoT replies
                    "grok-4-fast-non-reasoning",    // both fast tiers: $0.20/$0.50
                    "grok-4.3",                     // $1.25/$2.50
                    "grok-4.5"),                    // $2/$6 — flagship
            // OpenRouter's FREE tier (ids must keep the ':free' suffix — without it you are billed).
            // Pulled from its live /api/v1/models on 2026-08-22, which reports supported_parameters
            // per model, and filtered on ONE thing: response_format.
            //
            // ⚠️ That filter is not fussiness. A companion's every reply must be a JSON object, and
            // a backend that cannot be asked for one answers in prose — which this project has now
            // spent real sessions diagnosing, because it fails INTERMITTENTLY and reads as a flaky
            // model rather than a missing capability. 85% of all 421 models on OpenRouter support
            // response_format; only 7 of the 18 free ones do, so the free tier is exactly where
            // picking by name goes wrong. Ordered by how much model there is to hold the contract.
            //
            // ⚠️ Advertising response_format is NECESSARY AND NOT SUFFICIENT, which cost a turn to
            // learn. nemotron-nano-9b-v2:free advertises structured outputs and was suggested here on
            // that basis; on its first real session it emitted a correct, complete reply and then
            // several hundred blank lines and "</</</…" until it hit the token cap, never closing the
            // brace — a wasted turn and a duplicate call. It is dropped along with the 2.6B, because
            // a model too small to stop reliably is not made safe by what it advertises.
            //
            // Also deliberately absent, though free and larger: nemotron-3-ultra-550b, nemotron-3.5-
            // lightning, inkling, laguna, north-mini-code. None advertises response_format. The box
            // takes free text, so they remain usable — they are not RECOMMENDED, which is different.
            "https://openrouter.ai/api", List.of(
                    // 120B hybrid MoE, 12B active: big enough not to lose the envelope, cheap enough
                    // to stay fast, and the only one here whose own description names multi-agent work.
                    "nvidia/nemotron-3-super-120b-a12b:free", // structured outputs, 262k
                    // Dense 30.7B with native function calling — that training is what makes a model
                    // reliable at emitting a fixed object instead of talking about one.
                    "google/gemma-4-31b-it:free",             // response_format, 262k
                    "google/gemma-4-26b-a4b-it:free",         // response_format, 262k, MoE — faster
                    "dots-studio/dots-3-note-preview:free",   // structured outputs, 512k, 16B active
                    // LAST, and deliberately: Z.ai describe it as a reasoning model. It has the
                    // strongest structured-output support of the five — and the only 'stop' support —
                    // but it thinks before it answers and that thinking is billed against
                    // llm.maxTokens, which is the exact shape of the overrun logged on 2026-08-22.
                    // Raise maxTokens well past the 1000 floor before choosing it.
                    "z-ai/glm-5.2:free"));                    // structured outputs + stop, 256k

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

        // Editing state for the roster, gathered here so the saving runnable can rebuild the whole
        // "companions" array once every field's save consumer has run. addedName is a one-element
        // array only because a save consumer needs somewhere effectively-final to write.
        final List<EntryEditor> editors = readEditors(config);
        final String[] addedName = {""};

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("AI Companion"))
                .setSavingRunnable(() -> save(config, editors, addedName[0]));
        ConfigEntryBuilder eb = builder.entryBuilder();

        ConfigCategory companions = builder.getOrCreateCategory(Text.literal("Companions"));
        ConfigCategory llm = builder.getOrCreateCategory(Text.literal("LLM"));
        ConfigCategory tts = builder.getOrCreateCategory(Text.literal("Voice (TTS)"));
        ConfigCategory behavior = builder.getOrCreateCategory(Text.literal("Behavior"));
        ConfigCategory memory = builder.getOrCreateCategory(Text.literal("Memory"));
        ConfigCategory skills = builder.getOrCreateCategory(Text.literal("Skills"));
        ConfigCategory server = builder.getOrCreateCategory(Text.literal("Server"));

        // On every tab, because which config you are editing matters no matter where you land.
        // The Server tab is deliberately excluded: it says who owns it in its own words, and the
        // notice here is about YOUR file.
        for (ConfigCategory cat : List.of(companions, llm, tts, behavior, memory, skills)) {
            addScopeNotice(cat, eb);
        }

        buildCompanions(companions, eb, config, editors, addedName);
        buildLlm(llm, eb, config);
        buildTts(tts, eb, config);
        buildBehavior(behavior, eb, config);
        buildMemory(memory, eb, config);
        buildSkills(skills, eb, config);
        buildServer(server, eb, config);

        return builder.build();
    }

    /**
     * Says which {@code aicompanion.json} this screen is editing, and whether it takes effect.
     *
     * <p>This used to be a red warning that on a multiplayer server the screen "CANNOT change its
     * settings" — which was true, and was the whole problem. Identity, model, key, memory and voice
     * came from the operator's file, so every player on a server got the operator's companion,
     * spending the operator's tokens, and could change none of it.
     *
     * <p>Those are the player's own now: this file is read by this machine, and the roster is
     * announced to the server at join. So the notice says what applies where instead of apologising
     * for a screen that did nothing. What is still not yours lives on the Server tab, which says so
     * itself.
     */
    private static void addScopeNotice(ConfigCategory cat, ConfigEntryBuilder eb) {
        if (MinecraftClient.getInstance().getServer() != null) {
            cat.addEntry(eb.startTextDescription(Text.literal(
                            "Editing this world's config/aicompanion.json — changes apply on save.")
                    .formatted(Formatting.GRAY)).build());
            return;
        }
        cat.addEntry(eb.startTextDescription(Text.literal(
                        "These are YOUR settings and they apply on this server: your companions, "
                                + "your model and key, your memories, your voice. Reconnect after "
                                + "saving for a changed roster to reach the server. What the "
                                + "operator controls is on the Server tab.")
                .formatted(Formatting.GRAY)).build());
    }

    // ## Categories

    /**
     * One collapsible section per configured companion, plus a field to add another.
     *
     * <p>Edits go into {@code editors}, not straight into {@code config}: entries can be renamed,
     * removed and added in one sitting, so the {@code companions} array is rebuilt wholesale in
     * {@link #save} once every field's save consumer has run.
     */
    private static void buildCompanions(ConfigCategory cat, ConfigEntryBuilder eb, JsonObject config,
                                        List<EntryEditor> editors, String[] addedName) {
        cat.addEntry(eb.startTextDescription(Text.literal(
                        "Everything here applies when you save, including to companions already in the world. "
                                + "A companion spawned before this list existed picks up its settings on the next "
                                + "/companion reload."))
                .build());
        String live = liveCompanionNames();
        if (live != null) {
            cat.addEntry(eb.startTextDescription(Text.literal("In the world right now: " + live)).build());
        }

        for (EntryEditor editor : editors) {
            SubCategoryBuilder sub = eb.startSubCategory(Text.literal(editor.name()));
            JsonObject entry = editor.json();
            sub.add(eb.startStrField(Text.literal("Name"), str(entry, "name", ""))
                    .setDefaultValue("")
                    .setTooltip(
                            Text.literal("Shown above its head, used to label its speech in chat,"),
                            Text.literal("and how you address it: \"" + editor.name() + ", come here\""),
                            Text.literal("reaches this one and nobody else."))
                    .setSaveConsumer(v -> entry.addProperty("name", v))
                    .build());
            sub.add(eb.startStrField(Text.literal("Description"), str(entry, "description", ""))
                    .setDefaultValue("")
                    .setTooltip(Text.literal("A short third-person description of who this companion is."))
                    .setSaveConsumer(v -> entry.addProperty("description", v))
                    .build());
            sub.add(eb.startStrField(Text.literal("System Prompt"), str(entry, "systemPrompt", ""))
                    .setDefaultValue("")
                    .setTooltip(
                            Text.literal("Personality/style instructions injected into this"),
                            Text.literal("companion's system prompt. Applies live on save —"),
                            Text.literal("even to one already spawned."))
                    .setSaveConsumer(v -> entry.addProperty("systemPrompt", v))
                    .build());

            // Skin: dropdown of PNGs currently in config/aicompanion/skins/. The JSON form is either
            // a plain filename string or { file, slim }; normalize to the object form on save.
            JsonObject skin = skinSection(entry);
            String current = str(skin, "file", "");
            sub.add(eb.startSelector(Text.literal("Skin"), skinOptions(current),
                            current.isBlank() ? DEFAULT_SKIN : current)
                    .setDefaultValue(DEFAULT_SKIN)
                    .setTooltip(
                            Text.literal("Pick a 64×64 player-skin PNG. Drop new files into"),
                            Text.literal("config/aicompanion/skins/ and reopen this screen."))
                    .setSaveConsumer(v -> skin.addProperty("file", DEFAULT_SKIN.equals(v) ? "" : String.valueOf(v)))
                    .build());
            sub.add(eb.startStrField(Text.literal("Skin Username"), str(skin, "username", ""))
                    .setDefaultValue("")
                    .setTooltip(
                            Text.literal("Borrow any Minecraft player's skin — type a Mojang username."),
                            Text.literal("Nothing to install, and every client sees it, so this is the"),
                            Text.literal("option that works on a LAN. The Skin file above wins if both"),
                            Text.literal("are set. Needs the server to be online-mode with internet."))
                    .setSaveConsumer(v -> skin.addProperty("username", v == null ? "" : v.strip()))
                    .build());
            sub.add(eb.startBooleanToggle(Text.literal("Slim Arms"), bool(skin, "slim", false))
                    .setDefaultValue(false)
                    .setTooltip(
                            Text.literal("ON for slim (3px, Alex-style) arm skins, OFF for classic (4px, Steve-style)."),
                            Text.literal("Ignored when Skin Username is set — the account says which model it uses."))
                    .setSaveConsumer(v -> skin.addProperty("slim", v))
                    .build());

            // Voice lives here rather than on the TTS tab: it is part of an identity, and a roster
            // sharing one voice is hard to follow by ear. Blank stays meaningful — it defers to the
            // global tts.voice, so an existing config sounds exactly as it did.
            String currentVoice = str(entry, "voice", "");
            List<String> voiceOptions = new ArrayList<>();
            voiceOptions.add(DEFAULT_VOICE);
            voiceOptions.addAll(VOICE_SUGGESTIONS);
            if (!currentVoice.isBlank() && !voiceOptions.contains(currentVoice)) {
                voiceOptions.add(currentVoice); // a hand-picked id from the other 40 stays selectable
            }
            sub.add(eb.startStringDropdownMenu(Text.literal("Voice"),
                            currentVoice.isBlank() ? DEFAULT_VOICE : currentVoice, opaqueCells())
                    .setSelections(voiceOptions)
                    .setSuggestionMode(true)
                    .setDefaultValue(DEFAULT_VOICE)
                    .setTooltip(
                            Text.literal("Kokoro voice for this companion. af_/am_ = American"),
                            Text.literal("female/male, bf_/bm_ = British. Type to search, or"),
                            Text.literal("enter any id freely — the stack serves 68."),
                            Text.literal("List them: curl http://localhost:8880/v1/audio/voices"))
                    .setSaveConsumer(v -> entry.addProperty("voice",
                            DEFAULT_VOICE.equals(String.valueOf(v)) ? "" : String.valueOf(v)))
                    .build());
            sub.add(eb.startBooleanToggle(Text.literal("Remove On Save"), false)
                    .setDefaultValue(false)
                    .setTooltip(
                            Text.literal("Delete this companion from the config when you save."),
                            Text.literal("Does not despawn one already in the world —"),
                            Text.literal("use /companion despawn " + editor.name() + " for that."))
                    .setSaveConsumer(v -> editor.setRemoved(v))
                    .build());
            cat.addEntry(sub.build());
        }

        cat.addEntry(eb.startStrField(Text.literal("Add A Companion"), "")
                .setDefaultValue("")
                .setTooltip(
                        Text.literal("Type a name and save to add another companion."),
                        Text.literal("Reopen this screen to edit its description and skin."),
                        Text.literal("Then /companion spawn " + "<name>" + " to call it."))
                .setErrorSupplier(v -> {
                    String s = String.valueOf(v).trim();
                    if (s.isEmpty()) {
                        return java.util.Optional.empty(); // blank simply means "don't add one"
                    }
                    // Two companions answering to one name is the exact confusion the roster exists
                    // to prevent, so it is refused here rather than silently dropped on save.
                    boolean clash = editors.stream()
                            .anyMatch(e -> str(e.json(), "name", "").equalsIgnoreCase(s));
                    return clash
                            ? java.util.Optional.of(Text.literal("There is already a companion called " + s))
                            : java.util.Optional.empty();
                })
                .setSaveConsumer(v -> addedName[0] = v)
                .build());
    }

    /**
     * Mutable editing state for one companion: its JSON, the name it had when the screen opened (used
     * for the section header, which cannot change while the screen is up), and whether it is flagged
     * for removal.
     */
    private static final class EntryEditor {
        private final JsonObject json;
        private final String name;
        private boolean removed;

        EntryEditor(JsonObject json, String name) {
            this.json = json;
            this.name = name;
        }

        JsonObject json() {
            return this.json;
        }

        String name() {
            return this.name;
        }

        void setRemoved(boolean removed) {
            this.removed = removed;
        }

        boolean isRemoved() {
            return this.removed;
        }
    }

    /**
     * Comma-separated names of the companions currently in the world, or null when there is no local
     * server to ask.
     *
     * <p>Worth showing because "configured" and "spawned" are different things and the tab only
     * governs the first — a name listed here but not in the world just needs a {@code /companion
     * spawn}. Only answerable in singleplayer or as a LAN host; on a remote server the client has no
     * view of the entity list, so the line is omitted rather than guessed at.
     */
    private static String liveCompanionNames() {
        MinecraftServer server = MinecraftClient.getInstance().getServer();
        if (server == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof CompanionEntity companion) {
                    names.add(companion.displayName());
                }
            }
        }
        return names.isEmpty() ? "(none spawned)" : String.join(", ", names);
    }

    private static void buildLlm(ConfigCategory cat, ConfigEntryBuilder eb, JsonObject config) {
        JsonObject llm = section(config, "llm");
        // Combobox: type any URL, or pick a common provider from the dropdown. Suggestion mode
        // keeps free entry working; the list is just a convenience for first-time setup.
        cat.addEntry(eb.startStringDropdownMenu(Text.literal("Endpoint"), str(llm, "endpoint", "http://localhost:3030"), opaqueCells())
                .setSelections(LLM_ENDPOINT_SUGGESTIONS)
                .setSuggestionMode(true)
                .setDefaultValue("http://localhost:3030")
                .setTooltip(
                        Text.literal("OpenAI-compatible base URL — no trailing slash, no /v1"),
                        Text.literal("(the mod appends /v1/chat/completions itself)."),
                        Text.literal("Pick a suggestion or type any URL."),
                        Text.literal("Hosted endpoints also need a Model and API Key below."))
                .setErrorSupplier(v -> {
                    String s = String.valueOf(v).trim();
                    if (s.isEmpty()) {
                        return java.util.Optional.of(Text.literal("Endpoint is required"));
                    }
                    if (!s.startsWith("http://") && !s.startsWith("https://")) {
                        return java.util.Optional.of(Text.literal("Must start with http:// or https://"));
                    }
                    if (s.endsWith("/") || s.endsWith("/v1")) {
                        return java.util.Optional.of(Text.literal("Drop the trailing " + (s.endsWith("/v1") ? "/v1" : "slash") + " — the mod appends /v1/chat/completions"));
                    }
                    return java.util.Optional.empty();
                })
                .setSaveConsumer(v -> llm.addProperty("endpoint", String.valueOf(v)))
                .build());
        // Combobox listing every provider's models — the saved endpoint's provider first (so its
        // models top the dropdown), everyone else's after. The dropdown fuzzy-searches as the user
        // types, so switching endpoints mid-screen still reaches the right models by typing a few
        // letters; only the ordering waits for a save + reopen.
        String currentModel = str(llm, "model", "local");
        String savedEndpoint = str(llm, "endpoint", "http://localhost:3030").trim();
        LinkedHashSet<String> modelOptions = new LinkedHashSet<>();
        if (!currentModel.isBlank()) {
            modelOptions.add(currentModel); // keep whatever is configured selectable
        }
        modelOptions.addAll(LLM_MODEL_SUGGESTIONS.getOrDefault(savedEndpoint, List.of()));
        for (String endpoint : LLM_ENDPOINT_SUGGESTIONS) {
            modelOptions.addAll(LLM_MODEL_SUGGESTIONS.getOrDefault(endpoint, List.of()));
        }
        cat.addEntry(eb.startStringDropdownMenu(Text.literal("Model"), currentModel, opaqueCells())
                .setSelections(modelOptions)
                .setSuggestionMode(true)
                .setDefaultValue("local")
                .setTooltip(
                        Text.literal("Model sent with each request (llama.cpp ignores it)."),
                        Text.literal("Prefer a fast, non-reasoning model."),
                        Text.literal("Saved endpoint's models first, cheapest first;"),
                        Text.literal("type to search, or enter any name freely."),
                        Text.literal("OpenRouter: keep the ':free' suffix or you get billed."),
                        Text.literal("The suggested free ones are those that can be asked"),
                        Text.literal("for JSON — most free models cannot, and a companion"),
                        Text.literal("whose model answers in prose runs no commands."))
                .setSaveConsumer(v -> llm.addProperty("model", String.valueOf(v)))
                .build());
        cat.addEntry(eb.startDoubleField(Text.literal("Temperature"), dbl(llm, "temperature", 0.7))
                .setDefaultValue(0.7)
                .setTooltip(
                        Text.literal("Sampling temperature (0 = deterministic, higher = varied)."),
                        Text.literal("Negative = use the server's default."))
                .setSaveConsumer(v -> llm.addProperty("temperature", v))
                .build());
        cat.addEntry(eb.startIntField(Text.literal("Max Tokens"), intVal(llm, "maxTokens", 2000))
                .setDefaultValue(2000)
                .setTooltip(
                        Text.literal("Cap on tokens generated per reply. Never below 1000."),
                        Text.literal("It is a cap, not a budget — you pay for what is"),
                        Text.literal("generated, so a high value costs nothing on short"),
                        Text.literal("replies. Too low and skill commands are cut off"),
                        Text.literal("mid-reply, so nothing runs at all."),
                        Text.literal("Zero or negative = server default."),
                        Text.literal("Reasoning models (gpt-5.x, o-series, glm) count"),
                        Text.literal("hidden thinking here, and a small model that fails"),
                        Text.literal("to stop runs to the cap and is retried — which is"),
                        Text.literal("why the default is 2000, not the 1000 floor."))
                .setSaveConsumer(v -> llm.addProperty("maxTokens", v))
                .build());
        if (envApiKeySet()) {
            cat.addEntry(eb.startTextDescription(Text.literal(
                            "API Key: supplied by the AICOMPANION_LLM_APIKEY environment variable — the value in the file is ignored while that is set."))
                    .build());
        } else {
            cat.addEntry(eb.startStrField(Text.literal("API Key"), str(llm, "apiKey", ""))
                    .setDefaultValue("")
                    .setTooltip(
                            Text.literal("Bearer token for a hosted endpoint."),
                            Text.literal("Leave blank for local llama.cpp."),
                            Text.literal("Prefer the AICOMPANION_LLM_APIKEY env var"),
                            Text.literal("to keep the secret off disk."))
                    .setSaveConsumer(v -> llm.addProperty("apiKey", v))
                    .build());
        }
        cat.addEntry(eb.startIntField(Text.literal("Max Requests"), intVal(llm, "maxRequests", 0))
                .setDefaultValue(0)
                .setMin(0)
                .setTooltip(
                        Text.literal("Hard per-session cap on LLM requests —"),
                        Text.literal("the companion goes quiet once hit. 0 = unlimited."),
                        Text.literal("A cost guardrail for paid endpoints."))
                .setSaveConsumer(v -> llm.addProperty("maxRequests", v))
                .build());
        cat.addEntry(eb.startIntField(Text.literal("Max Concurrent Requests"),
                        intVal(llm, "maxConcurrentRequests", 2))
                .setDefaultValue(2)
                .setMin(1)
                .setMax(16)
                .setTooltip(
                        Text.literal("How many companions may be thinking at once."),
                        Text.literal("At 1 they take turns, so a second companion looks"),
                        Text.literal("frozen while the first works a long task."),
                        Text.literal("2 suits a local llama.cpp; raise it for a hosted"),
                        Text.literal("endpoint or a bigger roster. More slots = more"),
                        Text.literal("tokens burning at the same instant."))
                .setSaveConsumer(v -> llm.addProperty("maxConcurrentRequests", v))
                .build());
        cat.addEntry(eb.startLongField(Text.literal("Usage Report Every N Tokens"), longVal(llm, "usageReportEveryTokens", 100000L))
                .setDefaultValue(100000L)
                .setMin(0L)
                .setTooltip(
                        Text.literal("Print a running token-usage total to chat"),
                        Text.literal("every N tokens. Purely informational. 0 = never."))
                .setSaveConsumer(v -> llm.addProperty("usageReportEveryTokens", v))
                .build());
    }

    /**
     * Long-term memory, and the loud version of what it currently is.
     *
     * <p>The warning is not boilerplate. The facts are hard-coded fiction about a player who does
     * not exist, so a companion with this on will confidently refer to a bridge nobody built. Anyone
     * turning it on without knowing that will read it as the mod being broken, and the tooltip is
     * the only place they will find out.
     */
    private static void buildMemory(ConfigCategory cat, ConfigEntryBuilder eb, JsonObject config) {
        JsonObject memory = section(config, "memory");
        JsonObject embeddings = section(config, "embeddings");

        // ⚠️ This banner described the prototype for two releases after the prototype was gone —
        // "nothing is stored, nothing is learned" on the same screen as the switch that stores and
        // learns. Someone turning memory on read that, believed it, and went looking for a bug.
        cat.addEntry(eb.startTextDescription(Text.literal(
                        "Off by default.\n\n"
                                + "The companion looks up what it knows about you that fits what you "
                                + "just said, and those facts go into its context automatically. It "
                                + "never has to ask for them, so this works even on small local models.\n\n"
                                + "TURNING THIS ON STORES NOTHING BY ITSELF. Facts are written by "
                                + "/companion remember and /companion rememberhere, or automatically "
                                + "by \"Learn From Conversation\" below — which is off, because it "
                                + "spends an extra model call on every turn you talk.")
                .formatted(Formatting.YELLOW)).build());

        cat.addEntry(eb.startBooleanToggle(Text.literal("Enabled"), bool(memory, "enabled", false))
                .setDefaultValue(false)
                .setTooltip(
                        Text.literal("Turn on memory RECALL. Needs Embeddings below."),
                        Text.literal("This alone stores nothing — see the note above."))
                .setSaveConsumer(v -> memory.addProperty("enabled", v))
                .build());

        cat.addEntry(eb.startBooleanToggle(Text.literal("Learn From Conversation"),
                        bool(memory, "extractionEnabled", false))
                .setDefaultValue(false)
                .setTooltip(
                        Text.literal("After a reply has gone out, one extra model call reads"),
                        Text.literal("the exchange and stores what you stated about yourself."),
                        Text.literal("Without this, memory only ever holds what you typed into"),
                        Text.literal("/companion remember — telling a companion your dog's name"),
                        Text.literal("in chat is not remembered."),
                        Text.literal("Costs a call on every turn you talk (~$0.00013 on a"),
                        Text.literal("hosted model, nothing on a local one), and needs a model"),
                        Text.literal("that can return JSON on demand."))
                .setSaveConsumer(v -> memory.addProperty("extractionEnabled", v))
                .build());

        cat.addEntry(eb.startBooleanToggle(Text.literal("Skip Irrelevant Turns"),
                        bool(memory, "gateEnabled", true))
                .setDefaultValue(true)
                .setTooltip(
                        Text.literal("Decide whether a line is about you at all BEFORE looking"),
                        Text.literal("anything up. Stops \"attack that zombie\" from dredging up"),
                        Text.literal("a fact about your dog, and costs nothing when it skips."),
                        Text.literal("Leave this on: without it there is no reliable way to tell"),
                        Text.literal("a real question from small talk."))
                .setSaveConsumer(v -> memory.addProperty("gateEnabled", v))
                .build());

        cat.addEntry(eb.startIntSlider(Text.literal("Max Memories Per Reply"),
                        intVal(memory, "topK", 3), 1, 10)
                .setDefaultValue(3)
                .setTooltip(
                        Text.literal("Upper limit on how many facts can reach one reply."),
                        Text.literal("Usually only one or two actually clear the filters."),
                        Text.literal("Every one costs tokens on every turn."))
                .setSaveConsumer(v -> memory.addProperty("topK", v))
                .build());

        cat.addEntry(eb.startDoubleField(Text.literal("Minimum Relevance"),
                        dbl(memory, "minCosine", 0.45))
                .setDefaultValue(0.45)
                .setTooltip(
                        Text.literal("0 to 1. How closely a fact must match before it is used."),
                        Text.literal("Raise it if the companion brings up unrelated things;"),
                        Text.literal("lower it if it forgets things it obviously should know."),
                        Text.literal("Raise to 0.50 if you turn off Skip Irrelevant Turns."))
                .setSaveConsumer(v -> memory.addProperty("minCosine", v))
                .build());

        cat.addEntry(eb.startTextDescription(Text.literal(
                        "Embeddings — the lookup service memory needs. A SEPARATE server from the "
                                + "one in the LLM tab: a normal llama.cpp cannot do this job, and "
                                + "pointing this at it will not work. Run an embedding model of its "
                                + "own: \"ollama pull nomic-embed-text\", then leave the endpoint below.")
                .formatted(Formatting.GRAY)).build());

        cat.addEntry(eb.startBooleanToggle(Text.literal("Embeddings Enabled"),
                        bool(embeddings, "enabled", false))
                .setDefaultValue(false)
                .setTooltip(Text.literal("Required for memory. Harmless on its own."))
                .setSaveConsumer(v -> embeddings.addProperty("enabled", v))
                .build());

        cat.addEntry(eb.startStrField(Text.literal("Embeddings Endpoint"),
                        str(embeddings, "endpoint", "http://localhost:11434"))
                .setDefaultValue("http://localhost:11434")
                .setTooltip(Text.literal("Ollama's default port. No trailing slash, no /v1."))
                .setSaveConsumer(v -> embeddings.addProperty("endpoint", v))
                .build());

        cat.addEntry(eb.startStrField(Text.literal("Embeddings Model"),
                        str(embeddings, "model", "nomic-embed-text"))
                .setDefaultValue("nomic-embed-text")
                .setTooltip(
                        Text.literal("Changing this is not a preference — every setting on this"),
                        Text.literal("tab was tuned for nomic-embed-text at 768 dimensions, and a"),
                        Text.literal("model of a different width is refused rather than used."))
                .setSaveConsumer(v -> embeddings.addProperty("model", v))
                .build());
    }

    private static void buildTts(ConfigCategory cat, ConfigEntryBuilder eb, JsonObject config) {
        JsonObject tts = section(config, "tts");
        cat.addEntry(eb.startBooleanToggle(Text.literal("Enabled"), bool(tts, "enabled", false))
                .setDefaultValue(false)
                .setTooltip(
                        Text.literal("Voice output via a local Kokoro server."),
                        Text.literal("Start it first: cd tts && docker compose up -d."),
                        Text.literal("The CLIENT machine must reach the endpoint."))
                .setSaveConsumer(v -> tts.addProperty("enabled", v))
                .build());
        cat.addEntry(eb.startStrField(Text.literal("Endpoint"), str(tts, "endpoint", "http://localhost:8880"))
                .setDefaultValue("http://localhost:8880")
                .setTooltip(
                        Text.literal("Kokoro (OpenAI-compatible) TTS server base URL."),
                        Text.literal("YOUR machine fetches the audio, so this is YOUR setting"),
                        Text.literal("even on a dedicated server. Point it at a LAN address"),
                        Text.literal("(http://192.168.1.5:8880) to share one container."))
                .setSaveConsumer(v -> tts.addProperty("endpoint", v))
                .build());
        cat.addEntry(eb.startStrField(Text.literal("Model"), str(tts, "model", "kokoro"))
                .setDefaultValue("kokoro")
                .setSaveConsumer(v -> tts.addProperty("model", v))
                .build());
        // Voice moved to the Companions tab, per companion. tts.voice survives in the file as the
        // fallback for an entry that sets none, but editing it here would invite configuring one
        // voice for everybody — which is the thing worth avoiding.
        cat.addEntry(eb.startTextDescription(Text.literal(
                        "Voice is set per companion — see the Companions tab. A companion that "
                                + "picks none falls back to \"voice\" in aicompanion.json.")
                .formatted(Formatting.GRAY)).build());
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
                .setTooltip(
                        Text.literal("When set (e.g. \"@\"), only chat starting with it"),
                        Text.literal("reaches the companion. Blank = hears all nearby chat"),
                        Text.literal("— fine in singleplayer, costly on a paid endpoint."))
                .setSaveConsumer(v -> behavior.addProperty("triggerPrefix", v))
                .build());
        cat.addEntry(eb.startTextDescription(Text.literal(
                "Throttling, cross-talk, autonomy, building, defence and combat live on the Server "
                        + "tab now. They change the world everyone shares or cost the server ticks, "
                        + "so they are the operator's to set — and in singleplayer you are the "
                        + "operator, so you can still edit them there.")
                .formatted(Formatting.GRAY)).build());
    }

    /**
     * The operator's settings: editable when you are the operator, read-only when you are a guest.
     *
     * <p>This tab is the visible half of the config split. Everything else on this screen is yours
     * and applies wherever you play; these belong to whoever runs the server, and on someone else's
     * server the values shown are <b>theirs</b>, pushed over the wire at join — not this machine's
     * copy of the same keys, which nothing reads and which would be a convincing lie.
     */
    private static void buildServer(ConfigCategory cat, ConfigEntryBuilder eb, JsonObject config) {
        JsonObject remote = ClientConfigSync.serverPolicy();
        if (remote != null) {
            cat.addEntry(eb.startTextDescription(Text.literal(
                    "These are THIS SERVER's settings, shown read-only. Ask the operator to change "
                            + "them in config/aicompanion.json and run /companion reload.")
                    .formatted(Formatting.GOLD)).build());
            for (String key : new String[] {"maxCompanionsPerPlayer", "globalCompanionCap",
                    "allowPlayerCommands", "companionsAnswerAnyone", "maxRosterEntries",
                    "persistHistory", "thinkThrottleSeconds", "aiCrossTalk", "maxAutonomousTurns",
                    "buildCostsMaterials", "buildGroundCheck", "buildPhysicalPlacement",
                    "mobsTargetCompanion", "defenseFightBack", "defenseUseShield",
                    "defenseFleeFromHostiles", "defenseBravery", "autoEquipArmor", "scavengeFood",
                    "scavengeRadius", "attackDamageBase", "armorBase", "maxHealth", "followRange",
                    "advertiseInPrompt"}) {
                if (remote.has(key)) {
                    cat.addEntry(eb.startTextDescription(Text.literal(
                            "  " + key + ": " + remote.get(key).getAsString())
                            .formatted(Formatting.GRAY)).build());
                }
            }
            return;
        }

        JsonObject server = section(config, "server");
        cat.addEntry(eb.startTextDescription(Text.literal(
                "You are the operator here, so these apply. On someone else's server this tab shows "
                        + "their settings instead, read-only.").formatted(Formatting.GRAY)).build());
        cat.addEntry(eb.startIntField(Text.literal("Max Companions Per Player"),
                        intVal(server, "maxCompanionsPerPlayer", 2))
                .setDefaultValue(2).setMin(0)
                .setTooltip(
                        Text.literal("How many companions ONE player may have out."),
                        Text.literal("Each is a pathfinder on the server thread."),
                        Text.literal("0 = unlimited."))
                .setSaveConsumer(v -> server.addProperty("maxCompanionsPerPlayer", v))
                .build());
        cat.addEntry(eb.startIntField(Text.literal("Global Companion Cap"),
                        intVal(server, "globalCompanionCap", 20))
                .setDefaultValue(20).setMin(0)
                .setTooltip(
                        Text.literal("How many may exist on the whole server."),
                        Text.literal("The TPS brake: 20 players at 2 each is 40"),
                        Text.literal("pathfinders ticking. 0 = unlimited."))
                .setSaveConsumer(v -> server.addProperty("globalCompanionCap", v))
                .build());
        cat.addEntry(eb.startBooleanToggle(Text.literal("Allow Player Commands"),
                        bool(server, "allowPlayerCommands", true))
                .setDefaultValue(true)
                .setTooltip(
                        Text.literal("When off, /companion is operators only."),
                        Text.literal("The way to shut the mod off without"),
                        Text.literal("uninstalling it."))
                .setSaveConsumer(v -> server.addProperty("allowPlayerCommands", v))
                .build());
        cat.addEntry(eb.startBooleanToggle(Text.literal("Companions Answer Anyone"),
                        bool(server, "companionsAnswerAnyone", false))
                .setDefaultValue(false)
                .setTooltip(
                        Text.literal("When OFF (default), a companion answers only"),
                        Text.literal("its owner. A turn is billed to the OWNER, and"),
                        Text.literal("with clientBrain on, to the owner's machine —"),
                        Text.literal("so on with strangers about means they spend"),
                        Text.literal("your tokens and their words become your"),
                        Text.literal("companion's memories of you."),
                        Text.literal("On for a family LAN where that is the point."))
                .setSaveConsumer(v -> server.addProperty("companionsAnswerAnyone", v))
                .build());
        cat.addEntry(eb.startBooleanToggle(Text.literal("Persist Conversation History"),
                        bool(server, "persistHistory", true))
                .setDefaultValue(true)
                .setTooltip(
                        Text.literal("Whether conversation history survives a restart."),
                        Text.literal("It overlaps the memory corpus and does the"),
                        Text.literal("cross-session job worse — a companion re-reads"),
                        Text.literal("its own paraphrases and cites them as fact."),
                        Text.literal("Do NOT turn off until you have confirmed the"),
                        Text.literal("same details are landing in memory."))
                .setSaveConsumer(v -> server.addProperty("persistHistory", v))
                .build());
        cat.addEntry(eb.startDoubleField(Text.literal("Think Throttle (seconds)"),
                        dbl(server, "thinkThrottleSeconds", 0))
                .setDefaultValue(0.0).setMin(0.0)
                .setTooltip(
                        Text.literal("Minimum seconds between LLM turns; messages inside"),
                        Text.literal("the window queue into the next turn. 0 = no limit."))
                .setSaveConsumer(v -> server.addProperty("thinkThrottleSeconds", v))
                .build());
        cat.addEntry(eb.startBooleanToggle(Text.literal("Companion Cross-Talk"),
                        bool(server, "aiCrossTalk", false))
                .setDefaultValue(false)
                .setTooltip(
                        Text.literal("Whether companions overhear and answer EACH OTHER."),
                        Text.literal("Off by default, and worth leaving off: every"),
                        Text.literal("forwarded line is a full LLM turn, so two standing"),
                        Text.literal("together run up requests with nobody talking to"),
                        Text.literal("them — and each reply prompts another. One"),
                        Text.literal("measured session logged 382 of these."),
                        Text.literal("Only ever between one player's own companions."))
                .setSaveConsumer(v -> server.addProperty("aiCrossTalk", v))
                .build());
        cat.addEntry(eb.startIntField(Text.literal("Max Autonomous Turns"),
                        intVal(server, "maxAutonomousTurns", 2))
                .setDefaultValue(2).setMin(0)
                .setTooltip(
                        Text.literal("Actions the companion may take on its own after"),
                        Text.literal("finishing your request, before it waits to be"),
                        Text.literal("spoken to. Resets when anybody talks to it."),
                        Text.literal("0 = unlimited, and it will invent chores."))
                .setSaveConsumer(v -> server.addProperty("maxAutonomousTurns", v))
                .build());
        cat.addEntry(eb.startTextDescription(Text.literal(
                "Building, defence, scavenging and combat stats are in the \"server\" block of "
                        + "config/aicompanion.json — every key is documented there.")
                .formatted(Formatting.GRAY)).build());
    }

    /**
     * Read-only view of the loaded skills plus the one editable knob ({@code advertiseInPrompt}).
     * Editing skills themselves is a files-on-disk workflow (Cloth Config is the wrong tool for
     * multi-line markdown), matching how skins and TTS are configured — so this tab just lists what's
     * loaded and points at the folder.
     */
    private static void buildSkills(ConfigCategory cat, ConfigEntryBuilder eb, JsonObject config) {
        JsonObject skills = section(config, "skills");
        cat.addEntry(eb.startBooleanToggle(Text.literal("Advertise In Prompt"), bool(skills, "advertiseInPrompt", true))
                .setDefaultValue(true)
                .setTooltip(
                        Text.literal("Tell the companion each skill's name + description in its"),
                        Text.literal("system prompt, so you can also ask for one in chat."),
                        Text.literal("Skill bodies are injected only when invoked."))
                .setSaveConsumer(v -> skills.addProperty("advertiseInPrompt", v))
                .build());
        cat.addEntry(eb.startTextDescription(Text.literal(
                "Skills are markdown files in " + CompanionSkills.skillsDir()
                        + ". Edit the .md files, then run /companion reload. The list below is read-only."))
                .build());
        var loaded = CompanionSkills.all();
        if (loaded.isEmpty()) {
            cat.addEntry(eb.startTextDescription(Text.literal("(no skills loaded)")).build());
        } else {
            for (CompanionSkills.Skill s : loaded) {
                cat.addEntry(eb.startTextDescription(Text.literal(
                        s.key() + (s.description().isEmpty() ? "" : " — " + s.description()))).build());
            }
        }
    }

    // ## Save

    /**
     * Read the {@code companions} array into editable state, one editor per entry.
     *
     * <p>A config with no usable roster still gets one editor, seeded from the built-in defaults —
     * there must always be something on screen to edit, and always at least one companion to spawn.
     */
    private static List<EntryEditor> readEditors(JsonObject config) {
        List<EntryEditor> editors = new ArrayList<>();
        if (config.has("companions") && config.get("companions").isJsonArray()) {
            for (JsonElement el : config.getAsJsonArray("companions")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject entry = el.getAsJsonObject().deepCopy();
                String name = str(entry, "name", "");
                if (name.isBlank()) {
                    continue;
                }
                editors.add(new EntryEditor(entry, name));
            }
        }
        if (editors.isEmpty()) {
            editors.add(new EntryEditor(newEntry(CompanionConfig.name()), CompanionConfig.name()));
        }
        return editors;
    }

    /** A fresh roster entry: just a name, everything else left to the built-in defaults. */
    private static JsonObject newEntry(String name) {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", name);
        return entry;
    }

    /**
     * Rebuild {@code companions} from the edited state, write the file, then run the shared
     * reload/apply step on the integrated server.
     *
     * <p>Two things are refused rather than saved, because both fail silently and leave a config that
     * looks fine: an empty roster (nothing left to spawn) and duplicate names (two companions
     * answering to one name, which is the confusion the roster exists to remove).
     */
    private static void save(JsonObject config, List<EntryEditor> editors, String addedName) {
        JsonArray companions = new JsonArray();
        List<String> taken = new ArrayList<>();
        List<EntryEditor> kept = editors.stream().filter(e -> !e.isRemoved()).toList();
        if (kept.isEmpty() && !editors.isEmpty()) {
            // Every companion was flagged for removal. Keeping the first beats saving a config with
            // nobody in it and no way to add one back except by hand.
            kept = List.of(editors.get(0));
            AiCompanion.LOGGER.warn("[{}] config screen: refused to remove every companion — keeping {}",
                    AiCompanion.MOD_ID, str(kept.get(0).json(), "name", "the first"));
        }
        for (EntryEditor editor : kept) {
            String name = str(editor.json(), "name", "").trim();
            if (name.isEmpty()) {
                AiCompanion.LOGGER.warn("[{}] config screen: dropped a companion whose name was cleared",
                        AiCompanion.MOD_ID);
                continue;
            }
            if (taken.stream().anyMatch(n -> n.equalsIgnoreCase(name))) {
                AiCompanion.LOGGER.warn("[{}] config screen: dropped duplicate companion name '{}'",
                        AiCompanion.MOD_ID, name);
                continue;
            }
            editor.json().addProperty("name", name);
            taken.add(name);
            companions.add(editor.json());
        }
        String added = addedName == null ? "" : addedName.trim();
        if (!added.isEmpty() && taken.stream().noneMatch(n -> n.equalsIgnoreCase(added))) {
            JsonObject entry = newEntry(added);
            companions.add(entry);
            // Promote it to a real editor as well. The array is rebuilt from `editors` on every save,
            // and the Add field keeps its text until the screen is reopened — so without this, saving
            // a second time would rebuild the list without the companion just added.
            editors.add(new EntryEditor(entry, added));
            AiCompanion.LOGGER.info("[{}] config screen: added companion '{}'", AiCompanion.MOD_ID, added);
        }
        if (companions.isEmpty()) {
            // Everything was blank or duplicated. Leave the file's roster alone rather than writing
            // one that cannot spawn anybody.
            AiCompanion.LOGGER.warn("[{}] config screen: no usable companions after editing — "
                    + "leaving the existing list untouched", AiCompanion.MOD_ID);
        } else {
            config.add("companions", companions);
        }
        // The retired single-companion block, in case an old file was opened without a restart first.
        config.remove("companion");

        Path path = CompanionConfig.configPath();
        try {
            Files.writeString(path, PRETTY.toJson(config) + System.lineSeparator());
        } catch (IOException e) {
            AiCompanion.LOGGER.warn("[{}] config screen: failed to write {} ({}) — changes NOT saved",
                    AiCompanion.MOD_ID, path, e.toString());
            return;
        }
        // Re-announce the roster before anything else: the server holds a COPY taken at join, so a
        // companion added or renamed here does not exist for /companion spawn until it is sent
        // again. No-ops when there is nobody to send to.
        ClientConfigSync.announce();

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
            // Connected to a remote server: there is no local server to hand the file to, but this
            // JVM still runs the client-owned half — the brain's endpoint, the embeddings endpoint,
            // the Kokoro endpoint audio is fetched from. Without this the file saved and nothing
            // read it again until the game was relaunched, so editing an endpoint here looked like
            // it did nothing.
            CompanionConfig.reloadClientOwned();
            AiCompanion.LOGGER.info("[{}] config screen: saved + applied {} — this is the client's local "
                            + "config. The SERVER's rules (caps, permissions, chat routing) come from its "
                            + "own copy; edit config/aicompanion.json there and run /companion reload.",
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
     * filename; normalize that to {@code { file, username, slim }} so the fields have somewhere to live.
     */
    private static JsonObject skinSection(JsonObject companion) {
        if (companion.has("skin") && companion.get("skin").isJsonPrimitive()) {
            String file = companion.get("skin").getAsString();
            JsonObject obj = new JsonObject();
            obj.addProperty("file", file);
            obj.addProperty("username", "");
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

    /**
     * Cell creator whose cells paint a solid black background before the text. Cloth Config's
     * default dropdown cells are transparent, so the config list underneath bleeds through and
     * makes the suggestions unreadable.
     */
    private static DropdownBoxEntry.SelectionCellCreator<String> opaqueCells() {
        return new DropdownBoxEntry.DefaultSelectionCellCreator<>() {
            @Override
            public DropdownBoxEntry.SelectionCellElement<String> create(String value) {
                return new DropdownBoxEntry.DefaultSelectionCellElement<>(value, Text::literal) {
                    @Override
                    public void render(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height, float delta) {
                        graphics.fill(x, y, x + width, y + height, 0xFF000000);
                        super.render(graphics, mouseX, mouseY, x, y, width, height, delta);
                    }
                };
            }
        };
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
