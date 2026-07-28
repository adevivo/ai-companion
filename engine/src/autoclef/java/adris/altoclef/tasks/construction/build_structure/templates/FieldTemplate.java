package adris.altoclef.tasks.construction.build_structure.templates;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import adris.altoclef.tasks.construction.build_structure.StructureFromCode.SetBlockCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A watered crop field, flush with the terrain.
 *
 * <p>Deliberately the same layout the {@code farming} skill spells out in prose today — farmland grid,
 * water sources down the middle, crop on top, dirt-path border, torches on the border, chest at a
 * corner — so switching it to a generator changes the token cost and the arithmetic reliability
 * without changing what gets built.
 *
 * <p>Unlike the other templates the footprint is <b>centred</b> on the given position, again matching
 * what the skill asks for: the companion stands in the middle of where the field should go.
 */
public final class FieldTemplate implements StructureTemplate {

    private static final List<String> KEYWORDS = List.of("field", "farm", "crop", "farmland");

    /** Crop names to the block that grows, in the order they are searched for. */
    private static final List<String[]> CROPS = List.of(
            new String[] { "carrot", "carrots" },
            new String[] { "potato", "potatoes" },
            new String[] { "beetroot", "beetroots" },
            new String[] { "wheat", "wheat" });

    private static final int SMALL_SIDE = 5;
    private static final int MEDIUM_SIDE = 7;
    private static final int LARGE_SIDE = 9;
    private static final int MIN_SIDE = 3;
    /** The skill's own ceiling. Bigger than this and gathering seeds swallows the session. */
    private static final int MAX_SIDE = 15;

    /**
     * How many rows one water row keeps hydrated: itself plus four either side. Farmland further out
     * than that dries and the crop never grows, so the number of rows is derived from this rather
     * than fixed — a single row down the middle is only enough up to a depth of nine.
     */
    private static final int ROWS_PER_WATER_ROW = 9;
    private static final int TORCH_SPACING = 4;

    @Override
    public String name() {
        return "field";
    }

    @Override
    public Optional<TemplateRequest> parse(String description, Direction facing) {
        if (!DescriptionParser.mentionsAny(description, KEYWORDS)) {
            return Optional.empty();
        }
        Optional<BlockPos> base = DescriptionParser.position(description);
        if (base.isEmpty()) {
            return Optional.empty();
        }

        int side = switch (DescriptionParser.size(description)) {
            case MEDIUM -> MEDIUM_SIDE;
            case LARGE -> LARGE_SIDE;
            default -> SMALL_SIDE;
        };
        int width = side;
        int depth = side;
        Optional<int[]> explicit = DescriptionParser.dimensions(description);
        if (explicit.isPresent()) {
            width = explicit.get()[0];
            depth = explicit.get()[1];
        }
        if (width < MIN_SIDE || depth < MIN_SIDE || width > MAX_SIDE || depth > MAX_SIDE) {
            return Optional.empty();
        }

        // A field is one layer of crop over one layer of ground, so height is always 1 and the
        // material slot carries the crop.
        return Optional.of(new TemplateRequest(
                base.get(), width, depth, 1, crop(description),
                DescriptionParser.direction(description, facing), description));
    }

    /** The crop block named in the description, defaulting to wheat. */
    private static String crop(String description) {
        for (String[] entry : CROPS) {
            if (DescriptionParser.mentionsAny(description, List.of(entry[0]))) {
                return entry[1];
            }
        }
        return "wheat";
    }

    @Override
    public List<SetBlockCommand> generate(TemplateRequest request) {
        List<SetBlockCommand> plan = new ArrayList<>();
        BlockPos base = request.base();
        int width = request.width();
        int depth = request.depth();
        String crop = request.material();

        // Centred: the base position is the middle of the field, not a corner.
        int x0 = base.getX() - width / 2;
        int z0 = base.getZ() - depth / 2;
        int y = base.getY();
        Set<Integer> waterRows = waterRows(depth);

        for (int dz = 0; dz < depth; dz++) {
            boolean watered = waterRows.contains(dz);
            for (int dx = 0; dx < width; dx++) {
                if (watered) {
                    // Source blocks flush with the ground, with farmland on both sides holding them
                    // in. Air above so the channel stays walkable and nothing floats over it.
                    plan.add(new SetBlockCommand(x0 + dx, y, z0 + dz, "water"));
                    plan.add(new SetBlockCommand(x0 + dx, y + 1, z0 + dz, "air"));
                } else {
                    plan.add(new SetBlockCommand(x0 + dx, y, z0 + dz, "farmland"));
                    plan.add(new SetBlockCommand(x0 + dx, y + 1, z0 + dz, crop));
                }
            }
        }

        addBorder(plan, x0, y, z0, width, depth);
        return plan;
    }

    /**
     * Which rows carry water, spaced so no farmland is ever more than four rows from a source.
     *
     * <p>Enough rows to cover the depth, spread evenly and each sitting in the middle of the band it
     * waters. A field of nine rows or fewer therefore gets exactly one row down the centre, which is
     * the layout the farming skill has always asked for; deeper fields get as many as they need
     * rather than one row and a dry margin.
     */
    private static Set<Integer> waterRows(int depth) {
        int rows = Math.max(1, (depth + ROWS_PER_WATER_ROW - 1) / ROWS_PER_WATER_ROW);
        Set<Integer> result = new LinkedHashSet<>();
        for (int i = 0; i < rows; i++) {
            result.add((2 * i + 1) * depth / (2 * rows));
        }
        return result;
    }

    /**
     * A dirt-path ring one block outside the field, carrying the torches and a chest. The path also
     * stops the water spreading out of the channel at the field edge.
     */
    private void addBorder(List<SetBlockCommand> plan, int x0, int y, int z0, int width, int depth) {
        int minX = x0 - 1;
        int maxX = x0 + width;
        int minZ = z0 - 1;
        int maxZ = z0 + depth;
        int step = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (x != minX && x != maxX && z != minZ && z != maxZ) {
                    continue; // interior is the field itself
                }
                plan.add(new SetBlockCommand(x, y, z, "dirt_path"));
                if (step++ % TORCH_SPACING == 0) {
                    plan.add(new SetBlockCommand(x, y + 1, z, "torch"));
                }
            }
        }
        // Chest last so it wins the corner from whatever the ring put there.
        plan.add(new SetBlockCommand(minX, y + 1, minZ, "chest"));
    }
}
