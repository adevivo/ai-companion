package adris.altoclef.tasks.construction.build_structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.construction.build_structure.StructureFromCode.SetBlockCommand;
import adris.altoclef.tasks.construction.build_structure.templates.DescriptionParser;
import net.minecraft.core.BlockPos;

/**
 * Holds the plan of a build that could not afford itself, so retrying the same request builds the
 * same structure.
 *
 * <p>Without this, every {@code build_structure} attempt asks the model for a fresh design, and the
 * model designs a different building each time. A logged session went five rounds on one house and
 * the shortfall never converged, because each attempt priced a different structure:
 *
 * <pre>
 *   36 oak_planks, 163 oak_log, 4 glass_pane, crafting_table, furnace, 2 torch
 *   120 cobblestone, 6 glass, crafting_table, furnace, 3 torch
 *   44 stone, 2 glass
 *   153 oak_log
 *   100 oak_planks, 6 glass, 1 red_bed
 * </pre>
 *
 * <p>Fetching what it asked for could never work: by the time the logs were gathered it wanted
 * cobblestone. Pinning the plan makes "go and get what is missing, then build again" terminate.
 *
 * <p>Only plans that failed on <b>materials</b> are kept. A plan rejected for missing the ground, or
 * one that generated no blocks, is a bad plan — regenerating it is the right move.
 *
 * <h2>Why matching is fuzzy</h2>
 *
 * The obvious key — the description text — does not work. One owner asking the same thing three times
 * produced three different descriptions, because the model rephrases and the companion has walked a
 * few blocks in between:
 *
 * <pre>
 *   a small L-shaped house with a roof at (52, 62, 412)
 *   a small l-shaped house with a roof. Build at position (53, 62, 413)
 *   a small L-shaped house with a roof at (57, 62, 412)
 * </pre>
 *
 * An exact-match cache never fires on those. So entries are matched on the description with the
 * coordinates and the "build at position" scaffolding stripped out, plus an anchor position that only
 * has to be {@link #ANCHOR_TOLERANCE} blocks away. The consequence is deliberate: a replayed plan
 * builds where the <em>first</em> attempt was going to build, which may be a few blocks from where the
 * companion is now standing. That is the price of the retry terminating at all, and it is the same
 * spot the owner was standing in when they asked.
 */
public final class BuildPlanCache {

    /** Enough for a couple of builds in flight; this is a retry aid, not a library. */
    private static final int MAX_ENTRIES = 4;
    /**
     * A plan pins absolute coordinates and the terrain around them. Long enough to go and mine what
     * is missing, short enough that a plan never outlives the landscape it was drawn for.
     */
    private static final long TTL_MILLIS = 10 * 60 * 1000L;
    /**
     * How far the companion may have wandered between attempts and still mean the same building. The
     * logged retries drifted five blocks; a chunk is generous without merging genuinely separate
     * build sites.
     */
    private static final int ANCHOR_TOLERANCE = 16;

    /**
     * Words that vary freely between phrasings of the same request and identify nothing.
     *
     * <p>Dropped as whole tokens, never as substrings: "at" appears inside "flat" and "platform", and
     * a substring pass would quietly turn "a flat platform" into "a fl plform".
     */
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "at", "in", "on", "to", "of", "for", "me", "my",
            "please", "can", "could", "you", "build", "position", "coordinates", "coords",
            "here", "right");

    private record Entry(AltoClefController mod, String signature, BlockPos anchor,
            List<SetBlockCommand> plan, long storedAt) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private BuildPlanCache() {
    }

    /**
     * The description reduced to what actually identifies the structure: lowercased, with the
     * coordinates and connective scaffolding removed and whitespace collapsed.
     */
    static String signature(String description) {
        String text = (description == null ? "" : description).toLowerCase(Locale.ROOT);
        // Coordinates live in the anchor instead, and are the biggest source of spurious difference.
        text = text.replaceAll("\\(\\s*-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+\\s*\\)", " ");
        text = text.replaceAll("-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+", " ");
        // Punctuation and hyphenation vary freely ("L-shaped" / "l shaped"), so flatten both.
        text = text.replaceAll("[^a-z0-9]+", " ");
        StringBuilder result = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty() || STOPWORDS.contains(word)) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(word);
        }
        return result.toString();
    }

    /** Keep this plan for the next attempt at the same request. */
    public static synchronized void remember(AltoClefController mod, String description,
            List<SetBlockCommand> plan) {
        if (plan == null || plan.isEmpty()) {
            return;
        }
        String signature = signature(description);
        BlockPos anchor = anchorOf(description, plan);
        ENTRIES.removeIf(entry -> matches(entry, mod, signature, anchor) || isStale(entry));
        ENTRIES.add(new Entry(mod, signature, anchor, List.copyOf(plan), System.currentTimeMillis()));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.remove(0);
        }
    }

    /** The plan held for this request, if one is still fresh. */
    public static synchronized Optional<List<SetBlockCommand>> recall(AltoClefController mod,
            String description) {
        String signature = signature(description);
        BlockPos anchor = anchorOf(description, null);
        ENTRIES.removeIf(BuildPlanCache::isStale);
        // Last first: the most recent attempt at a request is the one worth replaying.
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            if (matches(ENTRIES.get(i), mod, signature, anchor)) {
                return Optional.of(ENTRIES.get(i).plan());
            }
        }
        return Optional.empty();
    }

    /** Drop the plan — the build either succeeded or is not worth retrying as drawn. */
    public static synchronized void forget(AltoClefController mod, String description) {
        String signature = signature(description);
        BlockPos anchor = anchorOf(description, null);
        ENTRIES.removeIf(entry -> matches(entry, mod, signature, anchor));
    }

    /**
     * Where the request is aimed. Taken from the description when it names coordinates, which every
     * prompt and skill requires it to; otherwise from the plan itself, so an entry is still anchored
     * somewhere sensible.
     */
    private static BlockPos anchorOf(String description, List<SetBlockCommand> plan) {
        Optional<BlockPos> stated = DescriptionParser.position(description);
        if (stated.isPresent()) {
            return stated.get();
        }
        if (plan != null && !plan.isEmpty()) {
            SetBlockCommand first = plan.get(0);
            return new BlockPos(first.x, first.y, first.z);
        }
        return null;
    }

    private static boolean matches(Entry entry, AltoClefController mod, String signature, BlockPos anchor) {
        if (entry.mod() != mod || !entry.signature().equals(signature)) {
            return false;
        }
        if (entry.anchor() == null || anchor == null) {
            // Nothing to compare on; the signature match has to carry it.
            return true;
        }
        return Math.abs(entry.anchor().getX() - anchor.getX()) <= ANCHOR_TOLERANCE
                && Math.abs(entry.anchor().getY() - anchor.getY()) <= ANCHOR_TOLERANCE
                && Math.abs(entry.anchor().getZ() - anchor.getZ()) <= ANCHOR_TOLERANCE;
    }

    private static boolean isStale(Entry entry) {
        return System.currentTimeMillis() - entry.storedAt() > TTL_MILLIS;
    }
}
