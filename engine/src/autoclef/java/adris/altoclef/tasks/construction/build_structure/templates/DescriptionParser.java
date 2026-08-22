package adris.altoclef.tasks.construction.build_structure.templates;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import adris.altoclef.tasks.construction.build_structure.BuildMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Pulls template parameters out of a {@code build_structure} description.
 *
 * <p>Everything here is deliberately literal. The point of the templates is to take the model out of
 * the loop for shapes that do not need designing, so anything requiring interpretation — an L-shaped
 * footprint, a second storey, "modern" — has to be declined rather than guessed at. See
 * {@link #disqualifier(String)}.
 */
public final class DescriptionParser {

    private DescriptionParser() {
    }

    /** {@code (66, 72, 307)} — the form every prompt and skill asks the agent to produce. */
    private static final Pattern POSITION =
            Pattern.compile("\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)");

    /** {@code 9x9}, {@code 7 x 11}, {@code 7x9x5}, {@code 7 by 9}. */
    private static final Pattern DIMENSIONS = Pattern.compile(
            "(\\d{1,3})\\s*(?:x|×|by)\\s*(\\d{1,3})(?:\\s*(?:x|×|by)\\s*(\\d{1,3}))?",
            Pattern.CASE_INSENSITIVE);

    /** {@code 20 blocks long}, {@code 12 long}, {@code 30 block bridge}. */
    private static final Pattern LENGTH = Pattern.compile(
            "(\\d{1,3})\\s*(?:blocks?\\s*)?(?:long|length)", Pattern.CASE_INSENSITIVE);

    /**
     * Phrases the rectangular generators cannot honour. Present in the description means no match at
     * any price: falling through to the DSL costs a round-trip, whereas building a plain box for
     * "L-shaped villa with a rose garden" spends the companion's materials on the wrong structure and
     * reports success.
     */
    private static final List<String> DISQUALIFIERS = List.of(
            "l-shaped", "l shaped", "t-shaped", "t shaped", "u-shaped", "u shaped",
            "storey", "story", "stories", "storeys", "second floor", "upstairs", "staircase", "stairs",
            "room", "rooms", "kitchen", "bedroom", "bathroom", "hallway", "corridor",
            "garden", "flower", "rose", "tree",
            "fireplace", "chimney", "furniture", "bookshelf", "painting",
            "balcony", "porch", "veranda", "courtyard", "basement", "cellar", "attic",
            "modern", "castle", "mansion", "villa", "tower", "dome", "arch", "pyramid",
            "window box", "moat", "fence around", "surrounded by");

    /**
     * Words people use instead of a block id. Everything resolves to a plain full cube — see the
     * full-blocks-only note on {@link StructureTemplate}.
     */
    private static final Map<String, String> MATERIAL_ALIASES = Map.ofEntries(
            Map.entry("oak", "oak_planks"),
            Map.entry("spruce", "spruce_planks"),
            Map.entry("birch", "birch_planks"),
            Map.entry("jungle", "jungle_planks"),
            Map.entry("acacia", "acacia_planks"),
            Map.entry("cherry", "cherry_planks"),
            Map.entry("mangrove", "mangrove_planks"),
            Map.entry("wood", "oak_planks"),
            Map.entry("wooden", "oak_planks"),
            Map.entry("plank", "oak_planks"),
            Map.entry("planks", "oak_planks"),
            Map.entry("log", "oak_log"),
            Map.entry("logs", "oak_log"),
            Map.entry("cobble", "cobblestone"),
            Map.entry("brick", "bricks"),
            Map.entry("stonebrick", "stone_bricks"),
            Map.entry("mud", "packed_mud"),
            Map.entry("sand", "sandstone"),
            Map.entry("snow", "snow_block"),
            Map.entry("quartz", "quartz_block"));

    /**
     * Suffixes of blocks that are not full cubes. Placed from their default state these come out
     * facing north, bottom-half, or unattached, so a template that used one would build something
     * visibly broken.
     */
    private static final List<String> NON_FULL_BLOCK_SUFFIXES = List.of(
            "_slab", "_stairs", "_door", "_trapdoor", "_fence", "_fence_gate", "_gate", "_button",
            "_pressure_plate", "_sign", "_carpet", "_pane", "_wall", "_bars", "_rod", "_torch",
            "_bed", "_banner", "_head", "_skull", "_pot", "_rail");

    /** Coarse size, resolved to actual dimensions by each template. */
    public enum SizeWord {
        SMALL, MEDIUM, LARGE, UNSPECIFIED
    }

    /** Lowercased with punctuation flattened to spaces, so token matching is uniform. */
    public static String normalize(String description) {
        return (description == null ? "" : description).toLowerCase(Locale.ROOT).replace('-', ' ');
    }

    /**
     * The phrase that rules templates out, or empty if the description is plain enough to build.
     * Returned rather than a boolean so the decline can say why in the log.
     */
    public static Optional<String> disqualifier(String description) {
        String text = normalize(description);
        for (String term : DISQUALIFIERS) {
            if (text.contains(term)) {
                return Optional.of(term);
            }
        }
        return Optional.empty();
    }

    /** The build position, which the description is required to carry. */
    public static Optional<BlockPos> position(String description) {
        Matcher matcher = POSITION.matcher(description == null ? "" : description);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))));
    }

    /**
     * Explicit dimensions, as {@code [width, depth]} or {@code [width, depth, height]}. Searched
     * after the coordinates have been cut out so a position can never be read as a size.
     */
    public static Optional<int[]> dimensions(String description) {
        String text = POSITION.matcher(description == null ? "" : description).replaceAll(" ");
        Matcher matcher = DIMENSIONS.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int width = Integer.parseInt(matcher.group(1));
        int depth = Integer.parseInt(matcher.group(2));
        if (matcher.group(3) == null) {
            return Optional.of(new int[] { width, depth });
        }
        return Optional.of(new int[] { width, depth, Integer.parseInt(matcher.group(3)) });
    }

    /** A single run length, for walls, paths and bridges. */
    public static OptionalInt length(String description) {
        String text = POSITION.matcher(description == null ? "" : description).replaceAll(" ");
        Matcher matcher = LENGTH.matcher(text);
        return matcher.find() ? OptionalInt.of(Integer.parseInt(matcher.group(1))) : OptionalInt.empty();
    }

    /** Coarse size word, which each template turns into its own defaults. */
    public static SizeWord size(String description) {
        String text = normalize(description);
        if (text.contains("small") || text.contains("tiny") || text.contains("little")) {
            return SizeWord.SMALL;
        }
        if (text.contains("large") || text.contains("big") || text.contains("huge")) {
            return SizeWord.LARGE;
        }
        if (text.contains("medium")) {
            return SizeWord.MEDIUM;
        }
        return SizeWord.UNSPECIFIED;
    }

    /**
     * The material to build from.
     *
     * <p>Two-word ids are tried before single words so "stone bricks" does not resolve as "stone",
     * and the first buildable word wins so "a cobblestone house with an oak door" builds in
     * cobblestone. A material named but not buildable as a wall — "made of oak trapdoors" — falls back
     * to the nearest word that is ("oak"), which reads the request the way a person would.
     *
     * <p>Always yields something; {@code fallback} when the description names no material at all.
     */
    public static Optional<String> material(String description, String fallback) {
        String[] tokens = normalize(description).split("[^a-z0-9_]+");
        // Longest match first: "stone bricks" must not be read as "stone".
        for (int i = 0; i + 1 < tokens.length; i++) {
            Optional<String> paired = resolveMaterial(tokens[i] + "_" + tokens[i + 1]);
            if (paired.isPresent()) {
                return paired;
            }
        }
        for (String token : tokens) {
            Optional<String> single = resolveMaterial(token);
            if (single.isPresent()) {
                return single;
            }
        }
        return Optional.of(fallback);
    }

    /**
     * Words that name a particular tree, as opposed to asking for wood in general.
     *
     * <p>"dark oak" needs no entry of its own: it tokenises to "dark" and "oak", and "oak" is already
     * here. Deliberately excludes "wood", "wooden", "plank(s)" and "log(s)" — those are the generic
     * words, and treating them as a choice of species is the whole bug this exists to answer.
     */
    private static final List<String> WOOD_SPECIES = List.of(
            "oak", "spruce", "birch", "jungle", "acacia", "cherry", "mangrove");

    /**
     * Whether the description picks a species of wood, rather than just asking for wood.
     *
     * <p>Read by {@link adris.altoclef.tasks.construction.build_structure.WoodChoice} to decide
     * whether it may retype a plan to whatever the companion is actually carrying. "A spruce house"
     * is an instruction and must be honoured even if there is no spruce to be had; "a wooden house"
     * is a description of the material, and any wood satisfies it.
     */
    public static boolean namesWoodSpecies(String description) {
        for (String token : normalize(description).split("[^a-z0-9_]+")) {
            if (WOOD_SPECIES.contains(token) || token.equals("dark_oak")) {
                return true;
            }
        }
        return false;
    }

    /**
     * One candidate word to a block id, or empty if it names nothing buildable.
     *
     * <p>Plurals are retried singular because descriptions say "oak planks" and "bricks" while some
     * ids are singular.
     */
    private static Optional<String> resolveMaterial(String candidate) {
        String alias = MATERIAL_ALIASES.get(candidate);
        if (alias != null) {
            return Optional.of(alias);
        }
        if (isBuildable(candidate)) {
            return Optional.of(candidate);
        }
        if (candidate.endsWith("s")) {
            String singular = candidate.substring(0, candidate.length() - 1);
            if (MATERIAL_ALIASES.containsKey(singular)) {
                return Optional.of(MATERIAL_ALIASES.get(singular));
            }
            if (isBuildable(singular)) {
                return Optional.of(singular);
            }
        }
        return Optional.empty();
    }

    /** A real block, payable out of the inventory, and a full cube. */
    public static boolean isBuildable(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return false;
        }
        for (String suffix : NON_FULL_BLOCK_SUFFIXES) {
            if (blockId.endsWith(suffix)) {
                return false;
            }
        }
        Block block = BuildMaterials.resolveBlock(blockId);
        if (block == Blocks.AIR) {
            return false;
        }
        // No consumed item means nothing to charge for, which for a wall material means the id
        // resolved to something the companion could never have carried.
        return BuildMaterials.consumedItemFor(block) != null;
    }

    /** An explicit compass direction, falling back to where the companion is looking. */
    public static Direction direction(String description, Direction fallback) {
        String text = normalize(description);
        if (text.contains("north")) {
            return Direction.NORTH;
        }
        if (text.contains("south")) {
            return Direction.SOUTH;
        }
        if (text.contains("east")) {
            return Direction.EAST;
        }
        if (text.contains("west")) {
            return Direction.WEST;
        }
        return fallback;
    }

    /** True when any of {@code keywords} appears in the description. */
    public static boolean mentionsAny(String description, List<String> keywords) {
        String text = normalize(description);
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
