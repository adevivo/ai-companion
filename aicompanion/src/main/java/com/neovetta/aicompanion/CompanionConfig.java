package com.neovetta.aicompanion;

import adris.altoclef.player2api.BehaviorConfig;
import adris.altoclef.player2api.Character;
import adris.altoclef.player2api.EmbeddingsConfig;
import adris.altoclef.player2api.LlmConfig;
import adris.altoclef.player2api.MemoryConfig;
import adris.altoclef.player2api.Prompts;
import adris.altoclef.player2api.ServerPolicy;
import adris.altoclef.player2api.TtsConfig;
import adris.altoclef.player2api.manager.ConversationManager;
import adris.altoclef.player2api.manager.TTSManager;
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
import net.minecraft.server.network.ServerPlayerEntity;
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
    /**
     * @param skinFile     PNG in {@code config/aicompanion/skins/}, or blank. Takes precedence over
     *                     {@code skinUsername}: an explicit local file is an intentional override.
     * @param skinUsername Mojang username to borrow a skin from, or blank. Resolved server-side by
     *                     {@link SkinProfileResolver} and pushed to clients, so unlike a file it needs
     *                     nothing installed on each machine.
     */
    public record RosterEntry(String name, String description, String persona,
                              String skinFile, String skinUsername, boolean skinSlim, String voice) {}

    /**
     * The identity used when the config supplies none, and the fallback for any field a roster entry
     * leaves out. There is deliberately no second config block to inherit from — identity lives in
     * {@code companions} and nowhere else.
     */
    private static final RosterEntry BUILT_IN_DEFAULT = new RosterEntry("Vetta",
            "A loyal, level-headed Minecraft companion who speaks plainly and watches your back.",
            "", "", "", false, "");

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
    public static String skinUsername() { return defaultEntry().skinUsername(); }
    public static boolean skinSlim() { return defaultEntry().skinSlim(); }

    /** One-line roster summary for the load log: {@code Ava(ava.png), Rook(@Notch), Vetta(default)}. */
    private static String describeRoster() {
        List<String> parts = new ArrayList<>();
        for (RosterEntry e : roster) {
            parts.add(e.name() + "(" + describeSkin(e) + ")");
        }
        return String.join(", ", parts);
    }

    /** How a roster entry's skin will be sourced, in the same precedence order the renderer uses. */
    private static String describeSkin(RosterEntry e) {
        if (!e.skinFile().isBlank()) {
            return e.skinFile();
        }
        return e.skinUsername().isBlank() ? "default" : "@" + e.skinUsername();
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
            // Also before the merge, and for the same reason: it moves the operator's tuned values
            // into 'server', and the merge would otherwise fill that block with defaults first.
            migrateServerBlock(root, changes);
            List<String> added = new ArrayList<>();
            addMissingRecursive(JsonParser.parseString(DEFAULT_JSON).getAsJsonObject(), root, "", added);
            if (!added.isEmpty()) {
                changes.add(String.format("filled in %d setting(s) added in a newer version (%s) — "
                        + "your existing values are untouched", added.size(), String.join(", ", added)));
            }
            rewrite(root, path, raw, changes);
            apply(root);
            AiCompanion.LOGGER.info(
                    "[{}] config loaded: roster={}, llm.endpoint={}, model={}, maxTokens={}, tts={}, brain={}, aiCrossTalk={}. Skins dir: {}",
                    AiCompanion.MOD_ID, describeRoster(), LlmConfig.baseUrl,
                    LlmConfig.model, LlmConfig.maxTokens,
                    TtsConfig.enabled ? TtsConfig.voice + " @ " + TtsConfig.endpoint : "off",
                    describeBrain(),
                    BehaviorConfig.aiCrossTalk, skinsDir());
            // On the line that always runs, for the same reason the brain is reported here: the
            // rules an operator set are invisible in game until somebody hits one, and "why can't
            // my players use this" is answered by this line rather than by reading a file on
            // another machine.
            AiCompanion.LOGGER.info("[{}] server policy: {}", AiCompanion.MOD_ID,
                    ServerPolicy.describe());
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
    /**
     * Which brain the config asks for, for the startup summary.
     *
     * <p>On the line that prints at <b>boot</b>, not only on reload. A companion thinking on the
     * server is indistinguishable in game from one thinking on the client — the only symptom of
     * "the client never took over" is a bill on the wrong account, which nobody notices during a
     * session. Reported here because this is the one place that always runs.
     *
     * <p>It names the {@code localMode} conflict explicitly rather than printing "client", because
     * client-side is ignored without it and a setting that reads as on while doing nothing is the
     * exact failure this line exists to prevent.
     */
    private static String describeBrain() {
        if (!LlmConfig.clientBrain) {
            return "server";
        }
        return LlmConfig.localMode
                ? "client-when-able"
                : "server (clientBrain IGNORED — needs llm.localMode=true)";
    }

    /**
     * Where this companion's thinking is actually happening, for a client's own reload line.
     *
     * <p>Observed, not configured. On a dedicated server the client is told to think by the server,
     * so its own {@code llm.clientBrain} says nothing about what is running — see
     * {@code ClientBrain#isThinkingHere}. Before the first turn there is nothing to observe yet, so
     * this says so rather than guessing.
     */
    private static String describeBrainHere() {
        if (thinkingHere) {
            return "this machine (the server delegates turns here)";
        }
        return LlmConfig.clientBrain
                ? "not yet asked to think here — the server decides, and has not delegated a turn"
                : "server (llm.clientBrain off in this file; the server's copy is what decides)";
    }

    /**
     * Set by {@code ClientBrain} when the server actually asks this client to think, cleared on each
     * connect. Lives here rather than on {@code ClientBrain} so shared code can read it without
     * loading a class that imports client-only Minecraft types — which on a dedicated server is a
     * {@code NoClassDefFoundError} rather than a false answer.
     */
    public static volatile boolean thinkingHere;

    public static int reloadAndApply(MinecraftServer server) {
        CompanionSkills.reload(); // pick up edited/added .md files before apply() re-advertises them
        load();
        // Build (or retry) the memory index. /companion reload updates the config statics but never
        // constructs a new AltoClefController, so without this, turning memory on and reloading
        // would appear to do nothing at all. Idempotent once it has succeeded, and a no-op when
        // memory is off.
        // Before warm(), not after: reload is "I have fixed it, try again", and warm() is what
        // re-checks the config and stages a fresh verdict. Clearing the latches afterwards would
        // throw that verdict away.
        adris.altoclef.player2api.MemoryHealth.rearm();
        adris.altoclef.player2api.CompanionMemory.warm(server);
        // A player who just started their Kokoro container is in the TTS back-off until it expires.
        // Reload is the obvious "I have fixed it, try again" signal, so honour it as one.
        TTSManager.clearUnavailable();
        // Same reasoning for skins: a mistyped username caches as "no skin" and would otherwise never
        // be retried, so correcting the config would appear to do nothing until a restart.
        SkinProfileResolver.clearCache();
        // ⚠️ Brigadier evaluates a node's requirement when the tree is SENT, not only when a command
        // runs — so flipping server.allowPlayerCommands leaves everyone's tab-completion showing the
        // old answer until they reconnect. Security holds either way (the predicate is re-checked on
        // execution), but an operator who has just locked the mod down and watched nothing change is
        // going to conclude the setting does not work.
        //
        // Their rules go out on the same pass, so the Server tab stops showing stale values too.
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            server.getPlayerManager().sendCommandTree(player);
            AiCompanion.sendServerPolicy(player);
        }
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
                    // Retune combat without a restart. Base values, so this is idempotent across
                    // repeated reloads and equipment modifiers still stack on top.
                    companion.applyCombatConfig();
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
     * Re-read the config file and apply only the player-owned half, for a client that has no local
     * server to hand the file to.
     *
     * <p>{@link #reloadAndApply} needs a {@link MinecraftServer} because it walks live entities, so
     * the config screen could only call it in singleplayer or on a LAN host. Connected to a
     * dedicated server it wrote the file and stopped there — leaving {@link LlmConfig#baseUrl},
     * {@link EmbeddingsConfig#baseUrl} and {@link TtsConfig#endpoint} holding whatever they were
     * given at mod init. The values that then ran were the ones from JVM startup, so changing an
     * endpoint appeared to require quitting and relaunching the game. It did.
     *
     * <p>The server-owned half is deliberately not applied: on a connected client those fields are
     * this player's wishes rather than the operator's rules, and the real ones arrive over the wire
     * for the read-only Server tab. See {@link ServerPolicy}.
     *
     * <p>Call on the client thread. Anything already baked into a spawned entity — name, skin, the
     * system prompt of a companion the server is running — is untouched, exactly as on the
     * singleplayer path.
     */
    public static void reloadClientOwned() {
        Path path = configPath();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            applyClientOwned(root);
            // Brain and memory named explicitly, because between them they decide WHICH MACHINE
            // holds the corpus and whether anything is ever written to it — the two questions a
            // player debugging "it doesn't remember" actually needs answered.
            //
            // ⚠️ NOT describeBrain() here. That reports this file's llm.clientBrain, which on a
            // connected client is a wish rather than a fact — the SERVER's copy decides. It printed
            // "brain=server" on a client that was running every turn and holding the corpus, which
            // is the one wrong answer this line exists to prevent. Ask what has actually happened.
            AiCompanion.LOGGER.info(
                    "[{}] client config reloaded: llm.endpoint={}, model={}, brain={}, "
                            + "embeddings.endpoint={}, memory={}, tts={}",
                    AiCompanion.MOD_ID, LlmConfig.baseUrl, LlmConfig.model, describeBrainHere(),
                    EmbeddingsConfig.baseUrl,
                    MemoryConfig.enabled
                            ? "recall on, extraction " + (MemoryConfig.extractionEnabled
                                    ? "on" : "OFF (nothing will be learned from conversation)")
                            : "off",
                    TtsConfig.enabled ? TtsConfig.voice + " @ " + TtsConfig.normalizedEndpoint() : "off");
        } catch (Exception e) {
            AiCompanion.LOGGER.warn("[{}] failed to re-read {} ({}) — keeping the values already loaded",
                    AiCompanion.MOD_ID, path, e.toString());
        }
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
        // ⚠️ The companion's own identity wins outright. This file is the OPERATOR's, and on a
        // dedicated server a player's companions are not in it — so consulting it first would let
        // /companion reload rewrite somebody else's companion into whatever the operator happens to
        // have configured under the same name, or into nothing at all.
        RosterEntry own = companion.identity();
        if (own != null) {
            return own;
        }
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

    /**
     * Keys that moved out of {@code behavior}, {@code combat} and {@code skills} into {@code server}
     * when configuration split by authority. Order is irrelevant; membership is what matters.
     */
    private static final List<String> MOVED_TO_SERVER = List.of(
            "thinkThrottleSeconds", "aiCrossTalk", "buildCostsMaterials", "buildGroundCheck",
            "buildPhysicalPlacement", "buildBlocksPerTick", "maxAutonomousTurns",
            "mobsTargetCompanion", "defenseFightBack", "defenseUseShield", "defenseFleeFromHostiles",
            "defenseBravery", "autoEquipArmor", "scavengeFood", "scavengeRadius",
            "attackDamageBase", "armorBase", "maxHealth", "followRange", "advertiseInPrompt");

    /**
     * Move the operator-owned settings into the new {@code server} block, keeping their values.
     *
     * <p>⚠️ <b>This has to run before {@link #addMissingRecursive}, and that ordering is the whole
     * point.</b> The merge fills in any key the file is missing from {@link #DEFAULT_JSON} — so on an
     * existing install it would see {@code server} absent, write the defaults into it, and then
     * {@code applyServerPolicy} would read those defaults instead of the values the operator had
     * tuned. Every server running this would quietly revert its combat stats, its build settings and
     * its autonomy cap on upgrade, with the old values still sitting in {@code behavior} looking
     * authoritative. Same trap, and the same fix, as {@link #migrateLegacyCompanion}.
     *
     * <p>Values are moved, not copied: leaving a stale duplicate behind is how a config grows two
     * places to edit, only one of which is read.
     */
    private static void migrateServerBlock(JsonObject root, List<String> changes) {
        if (root.has("server")) {
            return; // already split
        }
        JsonObject server = new JsonObject();
        List<String> moved = new ArrayList<>();
        for (String source : new String[] {"behavior", "combat", "skills"}) {
            JsonObject block = obj(root, source);
            if (block == null) {
                continue;
            }
            for (String key : MOVED_TO_SERVER) {
                if (block.has(key)) {
                    server.add(key, block.remove(key));
                    moved.add(source + "." + key);
                }
            }
            // The old _help text describes the keys that just left, so it is now documentation for
            // settings that are not in this block. Dropped rather than migrated: addMissingRecursive
            // refills it from DEFAULT_JSON on the same pass, and the new text is written for the
            // block it is actually in.
            for (String key : List.copyOf(block.keySet())) {
                if (key.startsWith("_")) {
                    block.remove(key);
                }
            }
            // A block that held nothing but moved settings is now empty. Leaving it would give the
            // file two plausible places to set combat stats, only one of which anything reads —
            // which is the trap the retired 'companion' block was removed to avoid.
            if (block.keySet().isEmpty()) {
                root.remove(source);
            }
        }
        // Written even when nothing moved, so a fresh-but-blockless file gets the key and
        // addMissingRecursive fills it from the defaults rather than this running again next boot.
        root.add("server", server);
        if (!moved.isEmpty()) {
            changes.add(String.format("moved %d operator setting(s) into the new 'server' block, "
                    + "keeping your values (%s) — these are the ones a connected player cannot change",
                    moved.size(), String.join(", ", moved)));
        }
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

    /**
     * Apply a parsed config file to the engine statics.
     *
     * <p>Split in two because there are now two kinds of setting and two machines reading this same
     * file. See {@link ServerPolicy} for the rule: the operator owns what costs ticks, changes the
     * shared world, or must bind everyone equally; the player owns what they pay for and what is
     * only their own experience.
     *
     * <p>Both halves are applied wherever the file is loaded, which is correct in singleplayer (one
     * JVM) and on a dedicated server (its own file is the authority). On a client that then connects
     * to someone else's server, the server-owned half is simply never consulted — no client-side
     * code reads it — and the operator's real values arrive separately, for display only. That is
     * why {@code applyServerPolicy} does not need to know where it is running.
     */
    private static void apply(JsonObject root) {
        applyServerPolicy(root);
        applyClientOwned(root);
    }

    /** Identity, model, key, memory, voice — everything the player pays for or lives with. */
    private static void applyClientOwned(JsonObject root) {
        // Advertise loaded skills in the persona so the owner can also invoke them in chat. Rebuilt
        // from the file value each apply(), so it never double-appends across reloads.
        //
        // Read from the server block, because the skill files are the server's: on a dedicated
        // server that is the authority, and on a client nothing builds a system prompt locally (it
        // arrives over the wire as the first message), so the value is inert there.
        JsonObject skills = obj(root, "server");
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
            LlmConfig.clientBrain = bool(llm, "clientBrain", LlmConfig.clientBrain);
            LlmConfig.clientBrainTimeoutMs =
                    intVal(llm, "clientBrainTimeoutMs", LlmConfig.clientBrainTimeoutMs);
            // API key: env/sysprop wins (so the secret need not live on disk); otherwise the file
            // value applies unconditionally. The check must be "did the env supply it?", not "is the
            // current value blank?" — after the first load the static holds the file's key, and a
            // blankness check would make /companion reload ignore any later edit to it.
            if (!apiKeySuppliedByEnv()) {
                LlmConfig.apiKey = str(llm, "apiKey", "");
            }
        }

        // Separate endpoint from the brain's, deliberately — a chat llama.cpp answers 501 to
        // /v1/embeddings, and even with --embeddings it would serve the wrong model at the wrong
        // width. See EmbeddingsConfig for the measurement behind that.
        JsonObject embeddings = obj(root, "embeddings");
        if (embeddings != null) {
            EmbeddingsConfig.enabled = bool(embeddings, "enabled", EmbeddingsConfig.enabled);
            EmbeddingsConfig.baseUrl = str(embeddings, "endpoint", EmbeddingsConfig.baseUrl);
            EmbeddingsConfig.model = str(embeddings, "model", EmbeddingsConfig.model);
            EmbeddingsConfig.expectedDimension =
                    intVal(embeddings, "expectedDimension", EmbeddingsConfig.expectedDimension);
            EmbeddingsConfig.timeoutMs = intVal(embeddings, "timeoutMs", EmbeddingsConfig.timeoutMs);
            EmbeddingsConfig.connectTimeoutMs =
                    intVal(embeddings, "connectTimeoutMs", EmbeddingsConfig.connectTimeoutMs);
            EmbeddingsConfig.maxConcurrentRequests =
                    intVal(embeddings, "maxConcurrentRequests", EmbeddingsConfig.maxConcurrentRequests);
            // Same env-wins rule as the brain's key, for the same reason.
            if (!EmbeddingsConfig.apiKeySuppliedByEnv()) {
                EmbeddingsConfig.apiKey = str(embeddings, "apiKey", "");
            }
        }

        JsonObject memory = obj(root, "memory");
        if (memory != null) {
            MemoryConfig.enabled = bool(memory, "enabled", MemoryConfig.enabled);
            MemoryConfig.topK = intVal(memory, "topK", MemoryConfig.topK);
            MemoryConfig.embedBudgetMs = intVal(memory, "embedBudgetMs", MemoryConfig.embedBudgetMs);
            MemoryConfig.minCosine = dbl(memory, "minCosine", MemoryConfig.minCosine);
            MemoryConfig.relativeMargin = dbl(memory, "relativeMargin", MemoryConfig.relativeMargin);
            MemoryConfig.gateEnabled = bool(memory, "gateEnabled", MemoryConfig.gateEnabled);
            MemoryConfig.seedDemoFacts = bool(memory, "seedDemoFacts", MemoryConfig.seedDemoFacts);
            MemoryConfig.extractionEnabled =
                    bool(memory, "extractionEnabled", MemoryConfig.extractionEnabled);
        }

        JsonObject behavior = obj(root, "behavior");
        if (behavior != null) {
            // The only setting left in this block, and the only one a player can act on from their
            // own file: it decides whether their chat costs them a turn. It is announced to the
            // server on join (see PlayerPreferences) precisely because chat routing is server-side —
            // a client-owned setting nobody reads would be a setting that silently does nothing.
            BehaviorConfig.triggerPrefix = str(behavior, "triggerPrefix", BehaviorConfig.triggerPrefix);
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
     * The operator's half: caps, the permission switches, and every setting that changes the shared
     * world or costs the server ticks.
     *
     * <p>These kept their existing statics ({@link BehaviorConfig}, {@link CombatConfig}) and only
     * moved which block they are read from, so nothing downstream had to change and a bug here shows
     * up as a wrong value rather than a missing one.
     */
    private static void applyServerPolicy(JsonObject root) {
        JsonObject server = obj(root, "server");
        if (server == null) {
            return;
        }

        ServerPolicy.maxCompanionsPerPlayer =
                intVal(server, "maxCompanionsPerPlayer", ServerPolicy.maxCompanionsPerPlayer);
        ServerPolicy.globalCompanionCap =
                intVal(server, "globalCompanionCap", ServerPolicy.globalCompanionCap);
        ServerPolicy.allowPlayerCommands =
                bool(server, "allowPlayerCommands", ServerPolicy.allowPlayerCommands);
        ServerPolicy.companionsAnswerAnyone =
                bool(server, "companionsAnswerAnyone", ServerPolicy.companionsAnswerAnyone);
        ServerPolicy.maxRosterEntries =
                intVal(server, "maxRosterEntries", ServerPolicy.maxRosterEntries);
        ServerPolicy.persistHistory = bool(server, "persistHistory", ServerPolicy.persistHistory);
        ServerPolicy.parkWhenOwnerOffline =
                bool(server, "parkWhenOwnerOffline", ServerPolicy.parkWhenOwnerOffline);
        ServerPolicy.serverAnswersWhenClientFails =
                bool(server, "serverAnswersWhenClientFails",
                        ServerPolicy.serverAnswersWhenClientFails);

        BehaviorConfig.thinkThrottleSeconds =
                dbl(server, "thinkThrottleSeconds", BehaviorConfig.thinkThrottleSeconds);
        BehaviorConfig.aiCrossTalk = bool(server, "aiCrossTalk", BehaviorConfig.aiCrossTalk);
        BehaviorConfig.buildCostsMaterials =
                bool(server, "buildCostsMaterials", BehaviorConfig.buildCostsMaterials);
        BehaviorConfig.buildGroundCheck =
                bool(server, "buildGroundCheck", BehaviorConfig.buildGroundCheck);
        BehaviorConfig.buildPhysicalPlacement =
                bool(server, "buildPhysicalPlacement", BehaviorConfig.buildPhysicalPlacement);
        BehaviorConfig.buildBlocksPerTick =
                intVal(server, "buildBlocksPerTick", BehaviorConfig.buildBlocksPerTick);
        BehaviorConfig.maxAutonomousTurns =
                intVal(server, "maxAutonomousTurns", BehaviorConfig.maxAutonomousTurns);
        BehaviorConfig.mobsTargetCompanion =
                bool(server, "mobsTargetCompanion", BehaviorConfig.mobsTargetCompanion);
        BehaviorConfig.defenseFightBack =
                bool(server, "defenseFightBack", BehaviorConfig.defenseFightBack);
        BehaviorConfig.defenseUseShield =
                bool(server, "defenseUseShield", BehaviorConfig.defenseUseShield);
        BehaviorConfig.defenseFleeFromHostiles =
                bool(server, "defenseFleeFromHostiles", BehaviorConfig.defenseFleeFromHostiles);
        BehaviorConfig.defenseBravery = dbl(server, "defenseBravery", BehaviorConfig.defenseBravery);
        BehaviorConfig.autoEquipArmor = bool(server, "autoEquipArmor", BehaviorConfig.autoEquipArmor);
        BehaviorConfig.scavengeFood = bool(server, "scavengeFood", BehaviorConfig.scavengeFood);
        BehaviorConfig.scavengeRadius = dbl(server, "scavengeRadius", BehaviorConfig.scavengeRadius);

        CombatConfig.attackDamageBase =
                dbl(server, "attackDamageBase", CombatConfig.attackDamageBase);
        CombatConfig.armorBase = dbl(server, "armorBase", CombatConfig.armorBase);
        CombatConfig.maxHealth = dbl(server, "maxHealth", CombatConfig.maxHealth);
        CombatConfig.followRange = dbl(server, "followRange", CombatConfig.followRange);
    }

    /** The operator-owned block as JSON, for the packet that shows a connected player the rules. */
    public static JsonObject serverPolicyJson() {
        try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            JsonObject server = obj(root, "server");
            if (server != null) {
                return server.deepCopy();
            }
        } catch (Exception e) {
            AiCompanion.LOGGER.warn("[{}] could not read the server block to send to a client ({})",
                    AiCompanion.MOD_ID, e.toString());
        }
        // Not the file's values but the ones actually in force, which is what a player needs to see.
        // Sending nothing would leave the Server tab blank on a server that simply has no block yet.
        JsonObject fallback = new JsonObject();
        fallback.addProperty("maxCompanionsPerPlayer", ServerPolicy.maxCompanionsPerPlayer);
        fallback.addProperty("globalCompanionCap", ServerPolicy.globalCompanionCap);
        fallback.addProperty("allowPlayerCommands", ServerPolicy.allowPlayerCommands);
        fallback.addProperty("companionsAnswerAnyone", ServerPolicy.companionsAnswerAnyone);
        fallback.addProperty("maxRosterEntries", ServerPolicy.maxRosterEntries);
        fallback.addProperty("persistHistory", ServerPolicy.persistHistory);
        return fallback;
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

        // skin: either a plain filename string, or { "file": "...", "username": "...", "slim": bool }.
        String file = fallback.skinFile();
        String username = fallback.skinUsername();
        boolean slim = fallback.skinSlim();
        if (o.has("skin") && !o.get("skin").isJsonNull()) {
            JsonElement skinEl = o.get("skin");
            if (skinEl.isJsonPrimitive()) {
                file = skinEl.getAsString();
            } else if (skinEl.isJsonObject()) {
                JsonObject skin = skinEl.getAsJsonObject();
                file = str(skin, "file", "");
                username = str(skin, "username", "").strip();
                slim = bool(skin, "slim", false);
            }
        }
        // Blank is meaningful: it means "no per-companion override", which sends the engine an empty
        // voiceIds[0] and lets it fall back to the global tts.voice.
        String voice = str(o, "voice", fallback.voice()).strip();

        return new RosterEntry(entryName.strip(), entryDescription, persona, file, username, slim,
                voice);
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
              "_helpCompanions": "Who your companions are. Every entry is a name you can spawn — /companion spawn Rook — and you can have several out at once. Fields: name, description, systemPrompt (personality/style, injected into the engine's hardened prompt rather than replacing it), skin { file, username, slim }, and voice (a Kokoro voice id such as af_heart or bm_george; blank = use tts.voice, so give each companion its own or they all sound alike). Two ways to give a companion a face. 'username' borrows any Minecraft player's skin — set it to a Mojang name and every client draws it, with nothing to install and the arm width taken from the account, which is the easy option and the only one that works properly on a LAN. 'file' is a 64x64 PNG you drop into config/aicompanion/skins/, named here; it wins over 'username' if you set both, and needs a copy on every machine that will see the companion. Blank for both = default Steve. slim = 3px (Alex) arms, and is ignored when 'username' is set because the account already says which model it uses. A username that cannot be looked up (offline-mode server, no internet, a typo) just falls back quietly - fix it and run /companion reload. Anything you leave out uses the built-in default. A bare /companion spawn takes the first entry that is not already out, so with two listed you can spawn both without naming either. Names matter beyond the label: 'Rook, go and scout north' reaches only Rook and the name is stripped before the model sees it, /companion stats Rook targets that one, and each companion's speech is labelled with its name in chat. Easiest way to edit this is in-game: /companion config, Companions tab.",
              "companions": [
                {
                  "name": "Vetta",
                  "description": "A loyal, level-headed companion who watches your back and speaks plainly.",
                  "systemPrompt": "You keep your replies short and spoken, like real dialogue. You are dry, practical, and a little wry, but always on your owner's side.",
                  "skin": { "file": "", "username": "", "slim": false },
                  "voice": ""
                }
              ],
              "llm": {
                "endpoint": "http://localhost:3030",
                "model": "local",
                "temperature": 0.7,
                "maxTokens": 2000,
                "timeoutMs": 90000,
                "useGrammar": true,
                "apiKey": "",
                "maxRequests": 0,
                "maxConcurrentRequests": 2,
                "maxPromptChars": 20000,
                "usageReportEveryTokens": 100000,
                "_maxPromptChars": "Hard character budget for the prompt; the oldest turns are dropped to fit (0 = no limit). Message count alone does not bound the prompt because every turn carries a world/agent status blob, so the same 64 messages can be 13k or 25k characters. Once the prompt outgrows what your model can attend to, the JSON contract at the FRONT is what gets lost: the companion still reasons correctly off recent turns and picks the right command, but writes it as prose instead of JSON, so nothing runs. Lower this if the companion talks sensibly and then stands still; raise it if your model has a large context. There is a FLOOR: the system prompt (~15k) and the newest turn with its status blob (~1.7k) can never be dropped, so a budget below ~17k throws away all conversation history on every turn and is still over — the companion then remembers nothing you said two messages ago. 20000 is the default for that reason. Watch the log for 'dropped ALL ... droppable turn(s)', which is what being under the floor looks like.",
                "_maxConcurrentRequests": "How many LLM requests may be in flight at once across ALL companions. At 1 the roster is single-file: while one companion is thinking, the others cannot, which makes a second companion look broken while the first works a long task. 2 suits a local llama.cpp, which serves one request at a time anyway. Raise it for a hosted endpoint that parallelises, or when several companions are out and expected to work independently — it is also the concurrency half of the spend guardrail, since every extra slot is another request that can be burning tokens at the same instant. Clamped to 1-16.",
                "_usage": "usageReportEveryTokens: print a running token-usage total to chat and the log every N tokens (0 = never). Purely informational — it never blocks a reply. maxRequests is the opposite: a hard per-session request cap that makes the companion stop responding once hit (0 = unlimited, the default). Leave maxRequests at 0 unless you are on a paid endpoint and want a hard stop.",
                "_apiKey": "Leave blank for a local llama.cpp server (no auth). For a paid hosted API, paste the key here — or better, leave this blank and set the AICOMPANION_LLM_APIKEY environment variable so the secret never lands on disk. The env var wins if both are set.",
                "_frontier": "To use a hosted OpenAI-compatible model instead of a local one, e.g. xAI/Grok: { \\"endpoint\\": \\"https://api.x.ai\\", \\"model\\": \\"grok-4-1-fast-non-reasoning\\", \\"apiKey\\": \\"xai-...\\" }. NOTE: endpoint is the base URL with NO trailing slash and NO /v1 — the mod appends /v1/chat/completions itself. Pick a non-reasoning model: reasoning models are slower and burn tokens on thinking the companion never uses.",
                "_maxTokens": "Caps the length of ONE reply. DEFAULT 2000; do not set it below 1000. It is a cap, not a budget — you are billed for the tokens actually generated, so a high value costs nothing on short answers, while a low one silently breaks things: skills hand the model a command to repeat verbatim (the farming one is ~700 characters), and if the reply is cut off mid-JSON no command runs at all and the companion just stands there. Use maxRequests / behavior.maxAutonomousTurns to control spend instead. 2000 rather than 1000 because the floor left no headroom at all: a reasoning model spends this on hidden thinking before it writes a word, and a small model that fails to STOP runs to the cap and gets retried — both were observed at 1000. 0 or less = omit it entirely and use the server's own default.",
                "_openai": "If endpoint is https://api.openai.com, prefer a non-reasoning model such as gpt-4.1-nano, gpt-4o-mini, or gpt-4.1. The gpt-5.x and o-series models are REASONING models: their hidden thinking counts against maxTokens, so on a small budget they can spend the whole thing thinking and return an EMPTY reply with no error — the companion just goes quiet. Give them 2000 or more. (They also ignore temperature; the mod omits it for them automatically.)"
              },
              "embeddings": {
                "enabled": false,
                "endpoint": "http://localhost:11434",
                "model": "nomic-embed-text",
                "expectedDimension": 768,
                "timeoutMs": 15000,
                "connectTimeoutMs": 5000,
                "maxConcurrentRequests": 1,
                "apiKey": "",
                "_help": "Turns text into vectors, so the companion can find the right memory later. Required by 'memory' below — memory with this off can rank nothing and only logs a warning. OLLAMA IS THE EASY PATH and can serve BOTH this and the brain from one process: 'ollama pull nomic-embed-text', then set endpoint to http://localhost:11434 here AND in 'llm'. Two environment variables matter before you start it: OLLAMA_MAX_LOADED_MODELS=2 so the brain and the embedder stay resident together (at 1 it evicts one to run the other on EVERY turn), and OLLAMA_KEEP_ALIVE=-1 so the embedder is not unloaded while you play — a cold embedder measured 363ms against 31-56ms warm. A plain llama.cpp CANNOT do this job: it answers /v1/embeddings with 501 'This server does not support embeddings', and --embeddings does not fix it, because one process serves one model and it would then embed with the chat model at the wrong width. With llama.cpp for the brain, run ollama alongside it for this and give them different endpoints. CHANGING THE MODEL LATER INVALIDATES EVERYTHING ALREADY STORED: vectors from one embedder mean nothing to another, so pick one and keep it.",
                "_expectedDimension": "The vector width this build expects, and a guard rather than a setting. All of the memory tuning — how much int8 compression costs the ranking, how many candidates to rescore, how many memories to recall — was measured at 768 dimensions with nomic-embed-text. A different embedding model produces a different width, and the mod refuses it rather than silently ranking in a space nothing was tuned for. Set to 0 only if you know you are re-tuning. Note that CHANGING the embedding model invalidates memories already stored: vectors from two models are not comparable, so recall gets quietly worse rather than erroring.",
                "_maxConcurrentRequests": "How many embedding requests may be in flight at once (clamped 1-8). Leave at 1. A local embedding server handles one batch at a time regardless, and if your embedding server and your LLM server share a GPU, concurrent work across the two is the thing most likely to run you out of VRAM mid-conversation."
              },
              "memory": {
                "enabled": false,
                "topK": 3,
                "minCosine": 0.45,
                "relativeMargin": 0.05,
                "gateEnabled": true,
                "seedDemoFacts": false,
                "extractionEnabled": false,
                "embedBudgetMs": 250,
                "_help": "Off by default. Long-term memory: before each turn the companion looks up what it knows about you that is relevant to what you just said, and those facts are put into its context automatically - it never has to ask for them, so it works even on small local models. Requires 'embeddings' above to be enabled and working. TWO THINGS SURPRISE PEOPLE. (1) Turning this on stores nothing by itself. Facts are written by /companion remember and /companion rememberhere, or automatically by 'extractionEnabled' below - which is OFF by default, so ordinary conversation is not remembered until you turn it on. (2) The folder config/aicompanion/memories/<your-uuid>/ is created by the first WRITE, not by enabling memory, so an absent folder means nothing has been stored yet rather than that memory is broken. WHICH MACHINE holds the corpus is decided by llm.clientBrain ON THE SERVER: off (the default) and the server thinks and keeps everyone's memories in its own config folder, so these settings in a player's file are never read; on, and each player's client thinks and keeps its own, and then this block is theirs. In singleplayer both are the same folder and the question does not arise.",
                "_topK": "How many remembered facts may reach the prompt (measured sweet spot is 5-10). Every one costs tokens on every turn, so on a paid endpoint this is money; retrieval quality is flat past 5.",
                "_seedDemoFacts": "Write a set of FICTIONAL demo memories into an empty store the first time memory loads. They describe a player who does not exist - a bridge over a ravine, a wolf named Biscuit - so a companion with them loaded will confidently discuss things that never happened. They existed to judge whether recall works before there was anything real to recall. Leave this off unless you are testing retrieval itself. Only ever written into an EMPTY store, so it cannot pollute real memories; turning it back off does not remove ones already written - delete the player folder under config/aicompanion/memories/ for that.",
                "_extractionEnabled": "Let the companion learn from conversation instead of only from /companion remember. OFF by default because it costs one extra LLM call on every turn you talk to it - measured at about $0.00013 a turn on a hosted model, roughly an eighth of the reply it accompanies, but it is your key and it should be your choice. The call happens AFTER the reply is sent, so it can never make the companion slower to answer, and if it fails nothing breaks - the turn just teaches it nothing. Expect that most of the time anyway: measured over a large corpus, 86% of turns contain no durable fact, so 'Memory: learned nothing from this turn' in the log is the normal outcome and not a fault. What it does store is a short third-person sentence like 'Alex prefers oak wood', scoped either to you (travels between worlds) or to the save (a base, a build, a place). It never decides on its own what is worth keeping: the model only reports subject/predicate/value and the mod decides the rest from a fixed table.",
                "_gateEnabled": "Decide whether a line is about you at all BEFORE looking anything up. This is what stops 'attack that zombie' dredging up a fact about your dog, and it costs nothing when it skips - no lookup happens. Leave it on: measured, there is no relevance threshold that separates a real question from small talk, because a meaningless line can match a fact more strongly than a genuine question does. If you turn this off, raise minCosine to 0.5.",
                "_relativeMargin": "Drop any fact this far behind the best match, even if it passed minCosine. The scores sit in a narrow band whether or not a fact is relevant, so 'is it close to the best one' sorts the tail better than 'is it above a line' does. Effect is flat anywhere from 0.03 to 0.08. Set to 0 to keep everything that clears minCosine.",
                "_minCosine": "How closely a memory must match what was just said before it is included, from 0 to 1. Without a floor the companion always gets topK facts even when you said 'hi', and weaves in whichever fact was least irrelevant. IMPORTANT: this cannot be tuned to be correct, only to pick which mistake you prefer. Measured, a meaningless line like 'attack that zombie' matches a fact about as strongly (0.51) as a real question like 'where is my dog?' does (0.46), so any setting that catches the second also catches the first. THE SHIPPED DEFAULT IS 0.45, not 0.5, and that is only safe because 'gateEnabled' above throws out meaningless turns BEFORE anything is scored - 0.45 then recovers two legitimate recalls that a 0.5 floor was dropping. If you turn the gate off, raise this back to 0.5 or the noise comes straight back. What 0.45 still cannot separate is two statements that share a frame: 'I have a <thing>' and a stored 'I have a <different, unrelated thing>' land around 0.49 on sentence shape alone - close enough to clear the floor with no shared meaning at all. Raise toward 0.5 if the companion brings up facts nobody asked about; expect it to miss some real ones in exchange.",
                "_embedBudgetMs": "Milliseconds a turn may wait for the lookup before giving up and answering without memories. This wait happens on the server thread, so it is capped deliberately: a healthy local embedder answers in about 15ms, and anything slower should not be allowed to stutter the game."
              },
              "tts": {
                "enabled": true,
                "endpoint": "http://localhost:8880",
                "model": "kokoro",
                "voice": "af_heart",
                "speed": 1.0,
                "_help": "Local voice output via Kokoro. On by default and self-arming: start the stack — 'cd config/aicompanion/tts && docker compose up -d' — and companions start speaking, no config edit needed. Until then it costs nothing, because the client reports back that it has nowhere to play audio and the server stops asking it for a few minutes. The MINECRAFT CLIENT calls this endpoint (the server only sends it the text), so it must be reachable from the CLIENT machine, not the server, and 'endpoint' is therefore read from each PLAYER'S OWN file — a dedicated server's copy of it is ignored. With the default localhost that means each player runs their own container; point it at a LAN address (http://192.168.1.5:8880) to share one. Voices: curl http://localhost:8880/v1/audio/voices. 'voice' here is only the FALLBACK — set a voice per companion under 'companions' so they can be told apart by ear. Only the companion's spoken 'message' is voiced — never commands or reasoning."
              },
              "behavior": {
                "triggerPrefix": "",
                "_help": "YOUR settings, read from YOUR OWN config file. On a multiplayer server this block is yours and the 'server' block below is the operator's. triggerPrefix: when set (e.g. \\"@\\"), only chat starting with it reaches the companion, and the prefix is stripped before the model sees it. Blank (default) = it answers all nearby chat, which is what you want in singleplayer. Set it on a paid endpoint or a shared world so ambient chatter costs nothing. Your prefix is announced to the server when you join, so it applies to what you type there and not just in singleplayer."
              },
              "server": {
                "maxCompanionsPerPlayer": 2,
                "globalCompanionCap": 20,
                "allowPlayerCommands": true,
                "companionsAnswerAnyone": false,
                "maxRosterEntries": 16,
                "persistHistory": true,
                "parkWhenOwnerOffline": true,
                "serverAnswersWhenClientFails": false,
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
                "defenseBravery": 2.0,
                "autoEquipArmor": true,
                "scavengeFood": true,
                "scavengeRadius": 16.0,
                "attackDamageBase": 1.0,
                "armorBase": 0.0,
                "maxHealth": 20.0,
                "followRange": 16.0,
                "advertiseInPrompt": true,
                "_helpScope": "THE OPERATOR'S BLOCK. On a dedicated server only the SERVER's copy of this block is read - a connected player's copy of it is ignored, and their in-game config screen shows these values read-only. Everything else in this file (companions, llm, embeddings, memory, tts, behavior) works the opposite way round: it is read from each player's OWN file, so they bring their own companions, their own model and API key, and their own memories, and they pay for their own tokens. The split is by who pays and who is affected. In singleplayer or on a LAN host there is one file and both halves come from it.",
                "_helpLimits": "maxCompanionsPerPlayer: how many companions ONE player may have out at once (0 = unlimited). globalCompanionCap: how many may exist on the whole server (0 = unlimited) - each one is a pathfinder running on the server thread, so this is the TPS brake. allowPlayerCommands: when false, /companion goes back to operators only - the player-facing subcommands demand permission level 2 instead of 0. Run /companion reload after changing it and they appear or disappear from tab-completion straight away. companionsAnswerAnyone: when false (default), a companion answers ONLY ITS OWNER in chat. Leave it false on any server where the players are not all trusted: a turn is billed to the companion's OWNER, and with llm.clientBrain on it is billed to their own machine - so a stranger talking to your companion spends your tokens and gets their sentence learned into your memories, under your name. Set it true on a family LAN where talking to each other's companions is the point. maxRosterEntries: how many identities one client may announce when it joins. persistHistory: whether conversation history survives a restart (default true). History and the memory corpus overlap and history does the cross-session job worse - it lets a companion re-read its own paraphrases every turn and cite them as fact - but do NOT turn it off until you have confirmed your companions are learning the same details into memory, because turning it off removes anything only history was holding.",
                "_helpBehavior": "thinkThrottleSeconds: minimum seconds between LLM turns (0 = no limit). Messages arriving inside the window are queued, not dropped — they fold into the next turn. buildCostsMaterials: when true (default), build_structure spends real items from the companion's inventory, one per block. If it is short it collects the shortfall itself and then builds, so one command does the whole job; blocks that are already correct are skipped and cost nothing. Set false for creative-style building where blocks come from nothing. buildGroundCheck: when true (default), a build plan is compared against the real terrain first. One-sided by design — a plan that came out below ground is lifted onto the surface (up to 3 blocks), while one above ground is built as generated ('on top of the ground' is a one-block gap, and towers are legitimately higher). Only a plan more than 16 blocks in the air is refused, without spending materials. Set false to disable the check entirely. maxAutonomousTurns: how many actions the companion may take on its own initiative after finishing what you asked, before it waits to be spoken to again (0 = unlimited). Every finished command prompts it for a next step, so without a cap one instruction can chain indefinitely — and it will invent chores. The counter resets whenever anybody talks to it. aiCrossTalk: whether companions overhear and answer EACH OTHER. Off by default, and worth leaving off — every forwarded line is a full LLM turn, so two companions standing together run up requests with nobody talking to them, and each reply prompts another reply. One measured session logged 382 of these, including four near-identical sentences in ninety seconds. Turn it on for the ambience of them chatting between themselves, on an endpoint where turns are free.",
                "_helpBuilding": "buildPhysicalPlacement: when true (default), the companion walks to the build site and places blocks by hand — a couple per tick, only what it can actually reach, moving along as each spot is used up, with an arm swing and a placement sound. Blocks are still written directly rather than right-clicked, so orientation-sensitive blocks are unaffected, but the build is situated and paced instead of appearing all at once from any distance. A build it cannot finish reaching says so and can be resumed by repeating the same description. Set false to restore the old instant behaviour (up to 256 blocks a tick, no reach check, no walking) if paced building misbehaves. buildBlocksPerTick: pacing when the above is on, clamped 1-64. Raise it to finish large builds sooner at the cost of blocks appearing in visible clumps.",
                "_helpArmor": "autoEquipArmor: whether the companion puts on better armour out of its own inventory without being asked (default true). Nothing else did this — hand it a full set of diamond and it would carry the set around and keep fighting in its shirt, with no message to say the armour was being ignored. It compares defence, toughness and Protection, so it never swaps a good piece for a worse one, and the piece it takes off goes back into the pack rather than being lost. Durability is ignored on purpose: a nearly-broken diamond helmet protects exactly as well as a fresh one right up until it breaks.", "_helpScavenge": "scavengeFood: whether the companion walks over to pick up food lying on the ground near it (default true). It only ever collects what it is standing on otherwise, and mob drops scatter several blocks, so without this it can clear out a herd of pigs and then go hungry later standing next to the pork. It fetches food only, ignores spider eyes (poison affects it even though rotten flesh does not), waits until nothing is hunting it, needs a free inventory slot, and never does this in preference to a job you have given it — tidying up happens once the work is done. scavengeRadius: how far it will walk for a dropped item, in blocks (default 16), which covers the spread of a fight it just had without turning it into a forager that chases anything edible on the horizon.", "_helpBravery": "defenseBravery: how many hostiles the companion reckons it can handle before its equipment is counted, only used when defenseFleeFromHostiles is on. The retreat maths came from a speedrunning bot, whose best play is to dodge every fight it can, and taken literally it rates an unarmoured companion holding a wooden sword at ONE hostile — so a companion at full health would run from a spider and a zombie it then killed without trouble the moment being cornered forced it to try. Since companions have a player's stat line, this is the missing term: what the body itself is worth before anything it is carrying. It scales with current health, so it makes a healthy companion brave without making a hurt one reckless. At the default of 2.0 it runs bare-handed from 3, with a wooden sword from 4, with a diamond sword from 7, and fully kitted with a shield from 12. Raise it for a companion that stands and fights; set it to 0 for the old bot-like caution.", "_helpDefense": "mobsTargetCompanion: when true (default), hostile mobs hunt the companion the way they hunt you. It is a LivingEntity rather than a real player, and vanilla mobs look for targets with a hard-coded player filter, so with this off they walk straight past it and it is only ever attacked in retaliation. Endermen are the exception either way — they keep player-only stare aggro. defenseFightBack: whether it deliberately engages hostiles that are targeting it (default true). It always swings at whatever is already in arm's reach regardless. defenseUseShield: whether it raises a shield when threatened (default true). defenseFleeFromHostiles: whether it may run away, dodge arrows and throw up cover blocks (default FALSE). All of that logic only becomes reachable once mobs can target the companion at all, so it has never run in a real world; leaving it off means the companion stands its ground and keeps working instead of abandoning a farm or a half-built house to sprint over the horizon. Turn it on once you have watched it get mobbed and decided you want flight.",
                "_helpCombat": "The companion's own combat stat line, before any weapon or armour it is holding. The defaults are exact player parity, which is the point: the companion is built on a zombie's attribute set, and a zombie has 3.0 attack damage and 2.0 armour where a player has 1.0 and 0.0. Left alone, that is triple damage bare-handed riding on top of whatever it holds — a diamond sword hitting for 10 where yours hits for 8 — plus two points of armour out of nowhere. Raise these if you want a tougher companion for a hard modpack; that is a fine thing to want, it just has to be asked for. followRange is how far it will look for something to fight, in blocks: 16 is a person's engagement distance, the zombie default of 35 is a mob's aggro radius and is far enough that it starts fights with things you cannot see. Changes apply to live companions on /companion reload.",
                "_helpSkills": "Markdown procedures in config/aicompanion/skills/*.md, invoked with /companion skill <name>. advertiseInPrompt: when true (default), the companion is told each skill's name + description in its system prompt, so you can also ask for one conversationally ('use your lumberjack skill'). Skill bodies are injected only when invoked, never in the standing prompt. Edit the .md files then run /companion reload to pick up changes."
              }
            }
            """;
}
