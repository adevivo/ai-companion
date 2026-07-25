package com.neovetta.aicompanion;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads user-authored "skills" — markdown procedures the owner invokes with {@code /companion skill
 * <name>}. A skill's body is injected verbatim into the companion's LLM queue and executed with the
 * model's normal command loop; this is prompt injection by design, not a macro system.
 *
 * <p>Files live in {@code config/aicompanion/skills/*.md} (two examples are unpacked on first run, the
 * same way {@link CompanionConfig} unpacks the TTS setup). Format is plain markdown, no YAML:
 * <ul>
 *   <li><b>Name</b> = first {@code # } heading (fallback: filename minus {@code .md}).</li>
 *   <li><b>Invocation key</b> = name lowercased, whitespace → {@code -}.</li>
 *   <li><b>Description</b> = first non-heading paragraph (used in listings + the prompt advert).</li>
 *   <li><b>Body</b> = everything below the heading, sent verbatim (capped at {@link #MAX_BODY}).</li>
 * </ul>
 * Malformed or empty files are skipped with a warning — a bad file never breaks the load.
 */
public final class CompanionSkills {

    /** One loaded skill. {@code key} is the invocation token; {@code body} is what the LLM receives. */
    public record Skill(String key, String name, String description, String body, Path file) {}

    /** Hard cap on body length, to protect the context window. Longer files are truncated + logged. */
    private static final int MAX_BODY = 4000;

    /**
     * The example skills shipped in the jar. Unpacked on first run and restorable with
     * {@code /companion skills reset} — a user file is never silently overwritten, because these are
     * meant to be edited.
     */
    private static final String[] BUNDLED =
            {"lumberjack.md", "home-guard.md", "farming.md", "harvest.md", "fishing.md"};

    // Insertion-ordered so listings/suggestions read in filename order. Guarded by the class monitor.
    private static final Map<String, Skill> SKILLS = new LinkedHashMap<>();

    private CompanionSkills() {}

    /** {@code config/aicompanion/skills/} — created on load. */
    public static Path skillsDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("aicompanion").resolve("skills");
    }

    /** Mod-init entry point: unpack the bundled examples, then scan the directory. */
    public static void load() {
        extractExamples();
        reload();
    }

    /** Re-scan the skills directory from disk. Called on init and from {@code /companion reload}. */
    public static synchronized void reload() {
        SKILLS.clear();
        Path dir = skillsDir();
        try {
            Files.createDirectories(dir);
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".md"))
                        .sorted()
                        .forEach(CompanionSkills::loadOne);
            }
            AiCompanion.LOGGER.info("[{}] loaded {} skill(s) from {}",
                    AiCompanion.MOD_ID, SKILLS.size(), dir);
        } catch (Exception e) {
            AiCompanion.LOGGER.warn("[{}] failed to scan skills dir {} ({})",
                    AiCompanion.MOD_ID, dir, e.toString());
        }
    }

    /** Parse one .md file and register it. Any failure is logged and the file skipped. */
    private static void loadOne(Path file) {
        try {
            String raw = Files.readString(file).strip();
            if (raw.isEmpty()) {
                AiCompanion.LOGGER.warn("[{}] skipping empty skill file {}", AiCompanion.MOD_ID, file.getFileName());
                return;
            }
            String[] lines = raw.split("\n", -1);

            String name = null;
            int headingIdx = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].strip().startsWith("# ")) {
                    name = lines[i].strip().substring(2).strip();
                    headingIdx = i;
                    break;
                }
            }
            if (name == null || name.isEmpty()) {
                String fn = file.getFileName().toString();
                name = fn.substring(0, fn.length() - 3); // minus ".md"
            }

            // Description: first non-blank, non-heading paragraph after the heading.
            StringBuilder desc = new StringBuilder();
            for (int i = headingIdx + 1; i < lines.length; i++) {
                String t = lines[i].strip();
                if (desc.isEmpty()) {
                    if (t.isEmpty() || t.startsWith("#")) {
                        continue;
                    }
                    desc.append(t);
                } else {
                    if (t.isEmpty()) {
                        break;
                    }
                    desc.append(' ').append(t);
                }
            }

            // Body: everything below the heading line, verbatim (or the whole file if no heading).
            String body;
            if (headingIdx >= 0) {
                StringBuilder b = new StringBuilder();
                for (int i = headingIdx + 1; i < lines.length; i++) {
                    b.append(lines[i]).append('\n');
                }
                body = b.toString().strip();
            } else {
                body = raw;
            }
            if (body.isEmpty()) {
                AiCompanion.LOGGER.warn("[{}] skipping skill '{}' — no body below the heading ({})",
                        AiCompanion.MOD_ID, name, file.getFileName());
                return;
            }
            if (body.length() > MAX_BODY) {
                AiCompanion.LOGGER.warn("[{}] skill '{}' body is {} chars — truncating to {} ({})",
                        AiCompanion.MOD_ID, name, body.length(), MAX_BODY, file.getFileName());
                body = body.substring(0, MAX_BODY);
            }

            String key = key(name);
            SKILLS.put(key, new Skill(key, name, desc.toString(), body, file));
        } catch (Exception e) {
            AiCompanion.LOGGER.warn("[{}] failed to load skill file {} ({}) — skipped",
                    AiCompanion.MOD_ID, file.getFileName(), e.toString());
        }
    }

    /** Normalize a name to its invocation key: lowercase, whitespace runs → single {@code -}. */
    public static String key(String name) {
        return name.strip().toLowerCase().replaceAll("\\s+", "-");
    }

    public static synchronized Skill get(String key) {
        return SKILLS.get(key);
    }

    public static synchronized Collection<Skill> all() {
        return List.copyOf(SKILLS.values());
    }

    public static synchronized Set<String> keys() {
        return Set.copyOf(SKILLS.keySet());
    }

    /**
     * One-line-per-skill block for the system prompt, so the owner can also ask for a skill in chat
     * ("use your lumberjack skill"). Bodies stay out of the standing prompt — only names + descriptions.
     * Empty string when no skills are loaded.
     */
    public static synchronized String advertisement() {
        if (SKILLS.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(
                "You have these skills your owner can invoke (by name, or via /companion skill <name>):");
        for (Skill s : SKILLS.values()) {
            sb.append("\n- ").append(s.key());
            if (!s.description().isEmpty()) {
                sb.append(" — ").append(s.description());
            }
        }
        return sb.toString();
    }

    /**
     * Unpack the bundled example skills to the skills dir if absent (same best-effort, per-file idiom
     * as {@link CompanionConfig#extractTtsSetup}). Never overwrites a file the user has edited.
     */
    /** Outcome of one {@link #resetBundled} attempt, for the command to report back. */
    public record ResetResult(String fileName, boolean restored, String detail) {}

    /**
     * Overwrite bundled example skills on disk with the versions from the jar, backing up whatever was
     * there to {@code <name>.md.bak} first, then re-scan so the change is live without a restart.
     *
     * <p>Exists because {@link #extractExamples()} deliberately never overwrites an existing file — that
     * protects user edits, but it also means a mod update silently leaves everyone on the old skill text
     * with no signal. Deleting the file and restarting worked, but "delete and restart" is not
     * discoverable, and doing only half of it (delete, then {@code /companion reload}) makes the skill
     * vanish until the next launch.
     *
     * @param key invocation key of a single bundled skill, or {@code null} to restore all of them
     */
    public static synchronized List<ResetResult> resetBundled(String key) {
        List<ResetResult> results = new ArrayList<>();
        Path dir = skillsDir();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            results.add(new ResetResult("(skills dir)", false, "could not create " + dir + ": " + e));
            return results;
        }
        for (String fileName : BUNDLED) {
            if (key != null && !key.equals(key(fileName.substring(0, fileName.length() - 3)))) {
                continue;
            }
            Path target = dir.resolve(fileName);
            try (InputStream in = CompanionSkills.class.getResourceAsStream("/aicompanion/skills/" + fileName)) {
                if (in == null) {
                    results.add(new ResetResult(fileName, false, "missing from the jar"));
                    continue;
                }
                String detail = "restored";
                if (Files.exists(target)) {
                    // Keep the user's version rather than destroying it — same .bak convention the
                    // config upgrade path uses, so there is always exactly one way to get edits back.
                    Path backup = dir.resolve(fileName + ".bak");
                    Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
                    detail = "restored (previous saved as " + backup.getFileName() + ")";
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                results.add(new ResetResult(fileName, true, detail));
                AiCompanion.LOGGER.info("[{}] reset skill {} from the jar", AiCompanion.MOD_ID, fileName);
            } catch (Exception e) {
                results.add(new ResetResult(fileName, false, e.toString()));
                AiCompanion.LOGGER.warn("[{}] failed to reset skill {} ({})",
                        AiCompanion.MOD_ID, fileName, e.toString());
            }
        }
        if (results.stream().anyMatch(ResetResult::restored)) {
            reload(); // pick the new text up immediately; the caller re-advertises it in the persona
        }
        return results;
    }

    /** The bundled skill keys, for tab-completion on {@code /companion skills reset}. */
    public static List<String> bundledKeys() {
        List<String> keys = new ArrayList<>();
        for (String fileName : BUNDLED) {
            keys.add(key(fileName.substring(0, fileName.length() - 3)));
        }
        return keys;
    }

    private static void extractExamples() {
        Path dir = skillsDir();
        try {
            Files.createDirectories(dir);
            for (String fileName : BUNDLED) {
                Path target = dir.resolve(fileName);
                if (Files.exists(target)) {
                    continue;
                }
                try (InputStream in = CompanionSkills.class.getResourceAsStream("/aicompanion/skills/" + fileName)) {
                    if (in == null) {
                        AiCompanion.LOGGER.warn("[{}] bundled skill missing from jar: {}", AiCompanion.MOD_ID, fileName);
                        continue;
                    }
                    Files.copy(in, target);
                    AiCompanion.LOGGER.info("[{}] wrote example skill {}", AiCompanion.MOD_ID, target);
                }
            }
        } catch (Exception e) {
            AiCompanion.LOGGER.warn("[{}] failed to unpack example skills to {} ({})",
                    AiCompanion.MOD_ID, dir, e.toString());
        }
    }
}
