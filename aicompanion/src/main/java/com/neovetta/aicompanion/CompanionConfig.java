package com.neovetta.aicompanion;

import adris.altoclef.player2api.BehaviorConfig;
import adris.altoclef.player2api.Character;
import adris.altoclef.player2api.LlmConfig;
import adris.altoclef.player2api.Prompts;
import adris.altoclef.player2api.TtsConfig;
import adris.altoclef.player2api.manager.ConversationManager;
import adris.altoclef.AltoClefController;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neovetta.aicompanion.entity.CompanionEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /**
     * One companion's identity: who it is, how it talks, and what it looks like.
     *
     * <p>{@code persona} is the finished text handed to {@link Character}, skills advertisement
     * already folded in — blank means "use the global {@link Prompts#persona}", which is how a config
     * with only the legacy {@code companion} block keeps working.
     */
    /**
     * @param voice Kokoro voice id for this companion, or blank to use the global {@code tts.voice}.
     *              Carried into {@code Character.voiceIds()[0]}, which the engine prefers over the
     *              global setting — see {@link #character(RosterEntry)}.
     */
    public record RosterEntry(String name, String description, String persona,
                              String skinFile, boolean skinSlim, String voice) {}

    /**
     * The identity used when the config supplies none, and the fallback for any field a roster entry
     * leaves out. There is deliberately no second config block to inherit from — identity lives in
     * {@code companions} and nowhere else.
     */
    private static final RosterEntry BUILT_IN_DEFAULT = new RosterEntry("Vetta",
            "A loyal, level-headed Minecraft companion who speaks plainly and watches your back.",
            "", "", false, "");

    /**
     * Every identity a companion can be spawned as, in file order; never empty.
     *
     * <p>The whole point of a list: identity used to be four statics, so {@code /companion spawn}
     * twice produced two bodies with the same name, the same personality and the same face. They
     * were indistinguishable in chat and every targeting command was a coin flip between them.
     */
    private static volatile List<RosterEntry> roster = List.of(BUILT_IN_DEFAULT);

    private CompanionConfig() {}

    /** Every configured identity, in file order. Never empty — falls back to a built-in default. */
    public static List<RosterEntry> roster() { return roster; }

    /** The identity a bare {@code /companion spawn} uses: the first in the file. */
    public static RosterEntry defaultEntry() { return roster.get(0); }

    /** Look up an identity by name, case-insensitively. */
    public static Optional<RosterEntry> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String wanted = name.strip();
        return roster.stream().filter(e -> e.name().equalsIgnoreCase(wanted)).findFirst();
    }

    // Convenience accessors for the default identity. Kept because most callers legitimately want
    // "the companion" (a fresh spawn, a fallback name in a log line) rather than a specific one.
    public static String name() { return defaultEntry().name(); }
    public static String description() { return defaultEntry().description(); }
    public static String skinFile() { return defaultEntry().skinFile(); }
    public static boolean skinSlim() { return defaultEntry().skinSlim(); }

    /** One-line roster summary for the load log: {@code Ava(ava.png), Rook(default)}. */
    private static String describeRoster() {
        List<String> parts = new ArrayList<>();
        for (RosterEntry e : roster) {
            parts.add(e.name() + "(" + (e.skinFile().isBlank() ? "default" : e.skinFile()) + ")");
        }
        return String.join(", ", parts);
    }

    /** Directory to drop skin PNGs into: {@code config/aicompanion/skins/}. Created on load. */
    public static Path skinsDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("aicompanion").resolve("skins");
    }

    /** Path of the config file ({@code config/aicompanion.json}). The config screen edits it in place. */
    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /**
     * The engine-facing identity (LLM name/description) built from current config. Rebuilt rather than
     * persisted, so config stays the single source of truth: editing {@code aicompanion.json} and
     * restarting re-identities existing companions. Used at spawn and when re-attaching a brain to a
     * companion restored from a save.
     */
    public static Character character() {
        return character(defaultEntry());
    }

    /**
     * The engine-facing identity for one roster entry, including its own persona and voice.
     *
     * <p>{@code voiceIds} carries the per-companion voice: {@code Player2APIService.textToSpeech}
     * uses {@code voiceIds[0]} when it is non-blank and falls back to the global {@code tts.voice}
     * otherwise, so an entry that sets no voice keeps the old behaviour exactly.
     */
    public static Character character(RosterEntry entry) {
        String n = entry.name();
        return new Character(n, n, "Hi, I'm " + n + " — your companion.", entry.description(), "",
                new String[]{entry.voice()}, entry.persona());
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
            extractTtsSetup();
            String raw = Files.readString(path);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            // Migration first: it creates "companions" from the retired block, and the merge below
            // would otherwise see that key absent and fill in DEFAULT_JSON's entry — handing everyone
            // a companion called Vetta in place of their own.
            List<String> changes = new ArrayList<>();
            migrateLegacyCompanion(root, changes);
            List<String> added = new ArrayList<>();
            addMissingRecursive(JsonParser.parseString(DEFAULT_JSON).getAsJsonObject(), root, "", added);
            if (!added.isEmpty()) {
                changes.add(String.format("filled in %d setting(s) added in a newer version (%s) — "
                        + "your existing values are untouched", added.size(), String.join(", ", added)));
            }
            rewrite(root, path, raw, changes);
            apply(root);
            AiCompanion.LOGGER.info(
                    "[{}] config loaded: roster={}, llm.endpoint={}, model={}, maxTokens={}, tts={}, aiCrossTalk={}. Skins dir: {}",
                    AiCompanion.MOD_ID, describeRoster(), LlmConfig.baseUrl,
                    LlmConfig.model, LlmConfig.maxTokens,
                    TtsConfig.enabled ? TtsConfig.voice + " @ " + TtsConfig.endpoint : "off",
                    BehaviorConfig.aiCrossTalk, skinsDir());
        } catch (Exception e) {
            AiCompanion.LOGGER.warn("[{}] failed to load {} ({}) — using built-in defaults",
                    AiCompanion.MOD_ID, path, e.toString());
        }
    }

    /**
     * Unpack the bundled TTS setup files (docker-compose.yml + README.md, shipped in the jar under
     * {@code aicompanion/tts/}) to {@code config/aicompanion/tts/}. Existing files are never
     * overwritten — admins edit ports/images in place. Best-effort: a failure here must not block
     * config loading.
     */
    private static void extractTtsSetup() {
        Path ttsDir = FabricLoader.getInstance().getConfigDir().resolve("aicompanion").resolve("tts");
        try {
            Files.createDirectories(ttsDir);
            for (String fileName : new String[] {"docker-compose.yml", "README.md"}) {
                Path target = ttsDir.resolve(fileName);
                if (Files.exists(target)) {
                    continue;
                }
                try (InputStream in = CompanionConfig.class.getResourceAsStream("/aicompanion/tts/" + fileName)) {
                    if (in == null) {
                        AiCompanion.LOGGER.warn("[{}] bundled TTS file missing from jar: {}", AiCompanion.MOD_ID, fileName);
                        continue;
                    }
                    Files.copy(in, target);
                    AiCompanion.LOGGER.info("[{}] wrote TTS setup file {}", AiCompanion.MOD_ID, target);
                }
            }
        } catch (Exception e) {
            AiCompanion.LOGGER.warn("[{}] failed to unpack TTS setup files to {} ({})",
                    AiCompanion.MOD_ID, ttsDir, e.toString());
        }
    }

    /**
     * Re-read the config file and apply it to a running server: the engine statics (LLM/TTS/behavior)
     * take effect on the next call that reads them, and the rebuilt persona is pushed into every live
     * companion's brain. Returns how many live companions were updated. Only companions in loaded
     * chunks are reached — one parked in an unloaded area keeps the old persona until reloaded again.
     *
     * <p>Shared "apply" step for {@code /companion reload} and the config screen's save hook. Call on
     * the server thread.
     */
    public static int reloadAndApply(MinecraftServer server) {
        CompanionSkills.reload(); // pick up edited/added .md files before apply() re-advertises them
        load();
        int updated = 0;
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof CompanionEntity companion) {
                    // Also the way back from an AI that switched itself off after repeated failures —
                    // the message it prints tells the owner to run exactly this command.
                    companion.resetAiFailures();
                    // Re-bind to this companion's OWN roster entry, not the default: with a roster,
                    // pushing one identity into every live brain is how two distinct companions turn
                    // back into clones on the first reload.
                    companion.applyRosterEntry(entryFor(companion));
                    AltoClefController ctrl = companion.getController();
                    if (ctrl != null && ctrl.getAIPersistantData() != null) {
                        ctrl.getAIPersistantData().updateSystemPrompt();
                        updated++;
                    }
                }
            }
        }
        return updated;
    }

    /**
     * Which roster entry a live companion belongs to: its stored roster name, or failing that its
     * display name.
     *
     * <p>The display-name fallback is for companions spawned before the roster existed. They carry a
     * blank {@code RosterName}, so a name lookup finds nothing and config edits would never reach
     * them — with no way to tell from in-game why one companion updates and another does not. A body
     * called Ava adopts the Ava entry on the next reload and behaves like any other from then on.
     *
     * <p>Null when neither matches, which {@code applyRosterEntry} treats as "leave it alone" rather
     * than reverting it to the default identity.
     */
    public static RosterEntry entryFor(CompanionEntity companion) {
        return find(companion.getRosterName())
                .or(() -> find(companion.displayName()))
                .orElse(null);
    }

    /** Pretty-printer for the rewritten config. HTML escaping OFF, or URLs and help text get mangled. */
    private static final Gson PRETTY =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * Fold any keys added since this config file was written into it, then rewrite it.
     *
     * <p>Without this, {@link #DEFAULT_JSON} is only ever written when the file is <em>absent</em>, so
     * every new setting is silently missing for anyone with an existing config — invisible in the file,
     * quietly falling back to its code default. That is exactly how {@code tts} once shipped without
     * anyone noticing it existed.
     *
     * <p>Strictly additive: existing values are never overwritten, only absent keys are filled in. The
     * previous file is kept as {@code aicompanion.json.bak} before any rewrite.
     */
    private static void rewrite(JsonObject config, Path path, String originalRaw, List<String> changes) {
        if (changes.isEmpty()) {
            return;
        }
        AiCompanion.LOGGER.info("[{}] updating {} — {}", AiCompanion.MOD_ID, FILE_NAME,
                String.join("; ", changes));
        try {
            Path backup = path.resolveSibling(FILE_NAME + ".bak");
            Files.writeString(backup, originalRaw);
            Files.writeString(path, PRETTY.toJson(config) + System.lineSeparator());
            AiCompanion.LOGGER.info("[{}] updated {} (previous version saved as {})",
                    AiCompanion.MOD_ID, path, backup.getFileName());
        } catch (Exception e) {
            // The in-memory changes already succeeded, so this run behaves correctly either way —
            // only the on-disk update (and therefore discoverability) is lost.
            AiCompanion.LOGGER.warn("[{}] could not rewrite {} ({}) — running with the changes in memory only",
                    AiCompanion.MOD_ID, path, e.toString());
        }
    }

    /**
     * Fold a pre-roster {@code companion} block into {@code companions} and drop the old key.
     *
     * <p>Identity used to live in a single {@code companion} object. The roster replaced it, and for
     * one release both were readable — which meant two places identity could live and only one of
     * them mattered. That is a trap: editing the block that is no longer authoritative saves cleanly
     * and changes nothing. One format, migrated once, is worth the one-way file rewrite.
     *
     * <p>The rewrite goes through {@link #rewrite}, so the original file survives as
     * {@code aicompanion.json.bak}.
     */
    private static void migrateLegacyCompanion(JsonObject root, List<String> changes) {
        if (!root.has("companion")) {
            return;
        }
        JsonElement legacy = root.remove("companion");
        JsonArray existing = arr(root, "companions");
        if (existing != null && !existing.isEmpty()) {
            // A hand-written roster is already the answer; the old block is just noise at this point.
            changes.add("removed the retired 'companion' block (your 'companions' list was already set)");
            return;
        }
        if (!legacy.isJsonObject()) {
            changes.add("removed an unreadable 'companion' block");
            return;
        }
        JsonObject entry = legacy.getAsJsonObject().deepCopy();
        JsonArray companions = new JsonArray();
        companions.add(entry);
        root.add("companions", companions);
        changes.add("moved your 'companion' settings into 'companions' (identity now lives in one place)");
    }

    /** Copy keys present in {@code defaults} but absent from {@code target}, recursing into objects. */
    private static void addMissingRecursive(JsonObject defaults, JsonObject target, String prefix,
                                            List<String> added) {
        for (Map.Entry<String, JsonElement> entry : defaults.entrySet()) {
            String key = entry.getKey();
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (!target.has(key)) {
                target.add(key, entry.getValue().deepCopy());
                added.add(path);
            } else if (entry.getValue().isJsonObject() && target.get(key).isJsonObject()) {
                addMissingRecursive(entry.getValue().getAsJsonObject(), target.getAsJsonObject(key), path, added);
            }
        }
    }

    private static void apply(JsonObject root) {
        // Advertise loaded skills in the persona so the owner can also invoke them in chat. Rebuilt
        // from the file value each apply(), so it never double-appends across reloads.
        JsonObject skills = obj(root, "skills");
        boolean advertise = skills == null || bool(skills, "advertiseInPrompt", true);
        String advert = advertise ? CompanionSkills.advertisement() : "";

        // Every roster entry carries its own persona, so the global is only the fallback for a
        // Character built outside the roster — it holds the skills advertisement and nothing else.
        Prompts.persona = advert;

        // Anything an entry omits falls back to the built-in defaults rather than to another config
        // block. There is deliberately no second place identity can live.
        RosterEntry defaults = BUILT_IN_DEFAULT;
        JsonArray companions = arr(root, "companions");
        List<RosterEntry> parsed = new ArrayList<>();
        if (companions != null) {
            for (JsonElement el : companions) {
                if (!el.isJsonObject()) {
                    continue;
                }
                RosterEntry entry = parseEntry(el.getAsJsonObject(), advert, defaults);
                if (entry.name().isBlank()) {
                    AiCompanion.LOGGER.warn("[{}] skipping a 'companions' entry with no name",
                            AiCompanion.MOD_ID);
                    continue;
                }
                if (parsed.stream().anyMatch(e -> e.name().equalsIgnoreCase(entry.name()))) {
                    // Duplicates are the exact failure this feature exists to prevent: two companions
                    // answering to one name is indistinguishable from having no names at all.
                    AiCompanion.LOGGER.warn("[{}] duplicate companion name '{}' in config — keeping the first",
                            AiCompanion.MOD_ID, entry.name());
                    continue;
                }
                parsed.add(entry);
            }
        }
        // The roster is never empty: an absent or entirely unusable array leaves the built-in default,
        // so a broken config still gives you a working companion rather than no way to spawn one.
        roster = parsed.isEmpty() ? List.of(BUILT_IN_DEFAULT) : List.copyOf(parsed);

        JsonObject llm = obj(root, "llm");
        if (llm != null) {
            LlmConfig.baseUrl = str(llm, "endpoint", LlmConfig.baseUrl);
            LlmConfig.model = str(llm, "model", LlmConfig.model);
            LlmConfig.temperature = dbl(llm, "temperature", LlmConfig.temperature);
            LlmConfig.maxTokens = intVal(llm, "maxTokens", LlmConfig.maxTokens);
            LlmConfig.timeoutMs = intVal(llm, "timeoutMs", LlmConfig.timeoutMs);
            LlmConfig.useGrammar = bool(llm, "useGrammar", LlmConfig.useGrammar);
            LlmConfig.maxRequests = intVal(llm, "maxRequests", LlmConfig.maxRequests);
            LlmConfig.maxPromptChars = intVal(llm, "maxPromptChars", LlmConfig.maxPromptChars);
            LlmConfig.maxConcurrentRequests =
                    intVal(llm, "maxConcurrentRequests", LlmConfig.maxConcurrentRequests);
            // Rebuild the worker pool if the cap moved — a reload that only changed the number would
            // otherwise keep the old pool for the rest of the session.
            ConversationManager.resizePool(LlmConfig.maxConcurrentRequests);
            LlmConfig.usageReportEveryTokens =
                    longVal(llm, "usageReportEveryTokens", LlmConfig.usageReportEveryTokens);
            // API key: env/sysprop wins (so the secret need not live on disk); otherwise the file
            // value applies unconditionally. The check must be "did the env supply it?", not "is the
            // current value blank?" — after the first load the static holds the file's key, and a
            // blankness check would make /companion reload ignore any later edit to it.
            if (!apiKeySuppliedByEnv()) {
                LlmConfig.apiKey = str(llm, "apiKey", "");
            }
        }

        JsonObject behavior = obj(root, "behavior");
        if (behavior != null) {
            BehaviorConfig.triggerPrefix = str(behavior, "triggerPrefix", BehaviorConfig.triggerPrefix);
            BehaviorConfig.thinkThrottleSeconds =
                    dbl(behavior, "thinkThrottleSeconds", BehaviorConfig.thinkThrottleSeconds);
            BehaviorConfig.aiCrossTalk = bool(behavior, "aiCrossTalk", BehaviorConfig.aiCrossTalk);
            BehaviorConfig.buildCostsMaterials =
                    bool(behavior, "buildCostsMaterials", BehaviorConfig.buildCostsMaterials);
            BehaviorConfig.buildGroundCheck =
                    bool(behavior, "buildGroundCheck", BehaviorConfig.buildGroundCheck);
            BehaviorConfig.buildPhysicalPlacement =
                    bool(behavior, "buildPhysicalPlacement", BehaviorConfig.buildPhysicalPlacement);
            BehaviorConfig.buildBlocksPerTick =
                    intVal(behavior, "buildBlocksPerTick", BehaviorConfig.buildBlocksPerTick);
            BehaviorConfig.maxAutonomousTurns =
                    intVal(behavior, "maxAutonomousTurns", BehaviorConfig.maxAutonomousTurns);
            BehaviorConfig.mobsTargetCompanion =
                    bool(behavior, "mobsTargetCompanion", BehaviorConfig.mobsTargetCompanion);
            BehaviorConfig.defenseFightBack =
                    bool(behavior, "defenseFightBack", BehaviorConfig.defenseFightBack);
            BehaviorConfig.defenseUseShield =
                    bool(behavior, "defenseUseShield", BehaviorConfig.defenseUseShield);
            BehaviorConfig.defenseFleeFromHostiles =
                    bool(behavior, "defenseFleeFromHostiles", BehaviorConfig.defenseFleeFromHostiles);
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

    /**
     * Read one identity from a {@code companions[]} entry, filling anything absent from
     * {@code fallback} (the built-in default).
     *
     * <p>{@code advert} is the skills advertisement, appended only when the entry brings a persona of
     * its own — a blank persona falls back to the global {@link Prompts#persona}, which is the
     * advertisement already, so appending here too would say it twice.
     */
    private static RosterEntry parseEntry(JsonObject o, String advert, RosterEntry fallback) {
        String entryName = str(o, "name", fallback.name());
        String entryDescription = str(o, "description", fallback.description());
        String persona = str(o, "systemPrompt", "");
        if (!persona.isBlank() && !advert.isEmpty()) {
            persona = persona + "\n\n" + advert;
        }

        // skin: either a plain filename string, or { "file": "...", "slim": bool }.
        String file = fallback.skinFile();
        boolean slim = fallback.skinSlim();
        if (o.has("skin") && !o.get("skin").isJsonNull()) {
            JsonElement skinEl = o.get("skin");
            if (skinEl.isJsonPrimitive()) {
                file = skinEl.getAsString();
            } else if (skinEl.isJsonObject()) {
                JsonObject skin = skinEl.getAsJsonObject();
                file = str(skin, "file", "");
                slim = bool(skin, "slim", false);
            }
        }
        // Blank is meaningful: it means "no per-companion override", which sends the engine an empty
        // voiceIds[0] and lets it fall back to the global tts.voice.
        String voice = str(o, "voice", fallback.voice()).strip();

        return new RosterEntry(entryName.strip(), entryDescription, persona, file, slim, voice);
    }

    /** Whether the LLM API key came from the launch environment (mirrors {@code LlmConfig.resolve}). */
    private static boolean apiKeySuppliedByEnv() {
        String v = System.getProperty("aicompanion.llm.apiKey");
        if (v == null || v.isBlank()) {
            v = System.getenv("AICOMPANION_LLM_APIKEY");
        }
        return v != null && !v.isBlank();
    }

    // ## JSON helpers (missing/mistyped fields fall back to the passed default)

    private static JsonObject obj(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static JsonArray arr(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonArray() ? parent.getAsJsonArray(key) : null;
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
              "_helpCompanions": "Who your companions are. Every entry is a name you can spawn — /companion spawn Rook — and you can have several out at once. Fields: name, description, systemPrompt (personality/style, injected into the engine's hardened prompt rather than replacing it), skin { file, slim }, and voice (a Kokoro voice id such as af_heart or bm_george; blank = use tts.voice, so give each companion its own or they all sound alike). Drop a 64x64 player-skin PNG into config/aicompanion/skins/ and set 'file' to its name; blank = default Steve, slim = 3px (Alex) arms. Anything you leave out uses the built-in default. A bare /companion spawn takes the first entry that is not already out, so with two listed you can spawn both without naming either. Names matter beyond the label: 'Rook, go and scout north' reaches only Rook and the name is stripped before the model sees it, /companion stats Rook targets that one, and each companion's speech is labelled with its name in chat. Easiest way to edit this is in-game: /companion config, Companions tab.",
              "companions": [
                {
                  "name": "Vetta",
                  "description": "A loyal, level-headed companion who watches your back and speaks plainly.",
                  "systemPrompt": "You keep your replies short and spoken, like real dialogue. You are dry, practical, and a little wry, but always on your owner's side.",
                  "skin": { "file": "", "slim": false },
                  "voice": ""
                }
              ],
              "llm": {
                "endpoint": "http://localhost:3030",
                "model": "local",
                "temperature": 0.7,
                "maxTokens": 1000,
                "timeoutMs": 90000,
                "useGrammar": true,
                "apiKey": "",
                "maxRequests": 0,
                "maxConcurrentRequests": 2,
                "maxPromptChars": 16000,
                "usageReportEveryTokens": 100000,
                "_maxPromptChars": "Hard character budget for the prompt; the oldest turns are dropped to fit (0 = no limit). Message count alone does not bound the prompt because every turn carries a world/agent status blob, so the same 64 messages can be 13k or 25k characters. Once the prompt outgrows what your model can attend to, the JSON contract at the FRONT is what gets lost: the companion still reasons correctly off recent turns and picks the right command, but writes it as prose instead of JSON, so nothing runs. Lower this if the companion talks sensibly and then stands still; raise it if your model has a large context.",
                "_maxConcurrentRequests": "How many LLM requests may be in flight at once across ALL companions. At 1 the roster is single-file: while one companion is thinking, the others cannot, which makes a second companion look broken while the first works a long task. 2 suits a local llama.cpp, which serves one request at a time anyway. Raise it for a hosted endpoint that parallelises, or when several companions are out and expected to work independently — it is also the concurrency half of the spend guardrail, since every extra slot is another request that can be burning tokens at the same instant. Clamped to 1-16.",
                "_usage": "usageReportEveryTokens: print a running token-usage total to chat and the log every N tokens (0 = never). Purely informational — it never blocks a reply. maxRequests is the opposite: a hard per-session request cap that makes the companion stop responding once hit (0 = unlimited, the default). Leave maxRequests at 0 unless you are on a paid endpoint and want a hard stop.",
                "_apiKey": "Leave blank for a local llama.cpp server (no auth). For a paid hosted API, paste the key here — or better, leave this blank and set the AICOMPANION_LLM_APIKEY environment variable so the secret never lands on disk. The env var wins if both are set.",
                "_frontier": "To use a hosted OpenAI-compatible model instead of a local one, e.g. xAI/Grok: { \\"endpoint\\": \\"https://api.x.ai\\", \\"model\\": \\"grok-4-1-fast-non-reasoning\\", \\"apiKey\\": \\"xai-...\\" }. NOTE: endpoint is the base URL with NO trailing slash and NO /v1 — the mod appends /v1/chat/completions itself. Pick a non-reasoning model: reasoning models are slower and burn tokens on thinking the companion never uses.",
                "_maxTokens": "Caps the length of ONE reply. Do not set it below 1000. It is a cap, not a budget — you are billed for the tokens actually generated, so a high value costs nothing on short answers, while a low one silently breaks things: skills hand the model a command to repeat verbatim (the farming one is ~700 characters), and if the reply is cut off mid-JSON no command runs at all and the companion just stands there. Use maxRequests / behavior.maxAutonomousTurns to control spend instead. 0 or less = omit it entirely and use the server's own default.",
                "_openai": "If endpoint is https://api.openai.com, prefer a non-reasoning model such as gpt-4.1-nano, gpt-4o-mini, or gpt-4.1. The gpt-5.x and o-series models are REASONING models: their hidden thinking counts against maxTokens, so on a small budget they can spend the whole thing thinking and return an EMPTY reply with no error — the companion just goes quiet. Give them 2000 or more. (They also ignore temperature; the mod omits it for them automatically.)"
              },
              "tts": {
                "enabled": false,
                "endpoint": "http://localhost:8880",
                "model": "kokoro",
                "voice": "af_heart",
                "speed": 1.0,
                "_help": "Local voice output via Kokoro. Start the stack first: 'cd tts && docker compose up -d', then set enabled=true. The MINECRAFT CLIENT calls this endpoint (the server only sends it the text), so it must be reachable from the client machine. Voices: curl http://localhost:8880/v1/audio/voices. 'voice' here is only the FALLBACK — set a voice per companion under 'companions' so they can be told apart by ear. Only the companion's spoken 'message' is voiced — never commands or reasoning."
              },
              "behavior": {
                "triggerPrefix": "",
                "thinkThrottleSeconds": 0,
                "aiCrossTalk": false,
                "buildCostsMaterials": true,
                "buildGroundCheck": true,
                "buildPhysicalPlacement": true,
                "buildBlocksPerTick": 2,
                "maxAutonomousTurns": 2,
                "mobsTargetCompanion": true,
                "defenseFightBack": true,
                "defenseUseShield": true,
                "defenseFleeFromHostiles": false,
                "_help": "triggerPrefix: when set (e.g. \\"@\\"), only chat starting with it reaches the companion, and the prefix is stripped before the model sees it. Blank (default) = it answers all nearby chat, which is what you want in singleplayer. Set it on a paid endpoint or a shared world so ambient chatter costs nothing. thinkThrottleSeconds: minimum seconds between LLM turns (0 = no limit). Messages arriving inside the window are queued, not dropped — they fold into the next turn. buildCostsMaterials: when true (default), build_structure spends real items from the companion's inventory, one per block. If it is short it collects the shortfall itself and then builds, so one command does the whole job; blocks that are already correct are skipped and cost nothing. Set false for creative-style building where blocks come from nothing. buildGroundCheck: when true (default), a build plan is compared against the real terrain first. One-sided by design — a plan that came out below ground is lifted onto the surface (up to 3 blocks), while one above ground is built as generated ('on top of the ground' is a one-block gap, and towers are legitimately higher). Only a plan more than 16 blocks in the air is refused, without spending materials. Set false to disable the check entirely. maxAutonomousTurns: how many actions the companion may take on its own initiative after finishing what you asked, before it waits to be spoken to again (0 = unlimited). Every finished command prompts it for a next step, so without a cap one instruction can chain indefinitely — and it will invent chores. The counter resets whenever anybody talks to it. aiCrossTalk: whether companions overhear and answer EACH OTHER. Off by default, and worth leaving off — every forwarded line is a full LLM turn, so two companions standing together run up requests with nobody talking to them, and each reply prompts another reply. One measured session logged 382 of these, including four near-identical sentences in ninety seconds. Turn it on for the ambience of them chatting between themselves, on an endpoint where turns are free.",
                "_helpBuilding": "buildPhysicalPlacement: when true (default), the companion walks to the build site and places blocks by hand — a couple per tick, only what it can actually reach, moving along as each spot is used up, with an arm swing and a placement sound. Blocks are still written directly rather than right-clicked, so orientation-sensitive blocks are unaffected, but the build is situated and paced instead of appearing all at once from any distance. A build it cannot finish reaching says so and can be resumed by repeating the same description. Set false to restore the old instant behaviour (up to 256 blocks a tick, no reach check, no walking) if paced building misbehaves. buildBlocksPerTick: pacing when the above is on, clamped 1-64. Raise it to finish large builds sooner at the cost of blocks appearing in visible clumps.",
                "_helpDefense": "mobsTargetCompanion: when true (default), hostile mobs hunt the companion the way they hunt you. It is a LivingEntity rather than a real player, and vanilla mobs look for targets with a hard-coded player filter, so with this off they walk straight past it and it is only ever attacked in retaliation. Endermen are the exception either way — they keep player-only stare aggro. defenseFightBack: whether it deliberately engages hostiles that are targeting it (default true). It always swings at whatever is already in arm's reach regardless. defenseUseShield: whether it raises a shield when threatened (default true). defenseFleeFromHostiles: whether it may run away, dodge arrows and throw up cover blocks (default FALSE). All of that logic only becomes reachable once mobs can target the companion at all, so it has never run in a real world; leaving it off means the companion stands its ground and keeps working instead of abandoning a farm or a half-built house to sprint over the horizon. Turn it on once you have watched it get mobbed and decided you want flight."
              },
              "skills": {
                "advertiseInPrompt": true,
                "_help": "Markdown procedures in config/aicompanion/skills/*.md, invoked with /companion skill <name>. advertiseInPrompt: when true (default), the companion is told each skill's name + description in its system prompt, so you can also ask for one conversationally ('use your lumberjack skill'). Skill bodies are injected only when invoked, never in the standing prompt. Edit the .md files then run /companion reload to pick up changes."
              }
            }
            """;
}
