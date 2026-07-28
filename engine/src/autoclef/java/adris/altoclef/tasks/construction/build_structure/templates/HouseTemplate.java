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
 * A single-room rectangular building: floor, hollow walls, flat roof, one doorway, window slots and
 * corner torches.
 *
 * <p>This is the shape the model was being asked to design from scratch on every request, at ~4.2k
 * prompt tokens a go, and the shape it kept getting wrong — the logged failure was a 5x5 house whose
 * generated plan wanted doors and stairs the companion could not obtain.
 */
public final class HouseTemplate implements StructureTemplate {

    private static final List<String> KEYWORDS =
            List.of("house", "hut", "shelter", "cabin", "shack", "shed", "home");

    /** width, depth, wall courses. Small is the default: cheap enough to actually finish. */
    private static final int[] SMALL = { 7, 7, 3 };
    private static final int[] MEDIUM = { 9, 11, 4 };
    private static final int[] LARGE = { 13, 15, 5 };

    /** Below this there is no interior left once the walls are in. */
    private static final int MIN_SIDE = 4;
    /** Above this a "house" is really a project, and the DSL's design sense is worth the tokens. */
    private static final int MAX_SIDE = 24;
    private static final int MIN_HEIGHT = 3;
    private static final int MAX_HEIGHT = 8;

    @Override
    public String name() {
        return "house";
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

        int[] defaults = switch (DescriptionParser.size(description)) {
            case MEDIUM -> MEDIUM;
            case LARGE -> LARGE;
            default -> SMALL;
        };
        int width = defaults[0];
        int depth = defaults[1];
        int height = defaults[2];

        // An explicit size always wins over a size word: "a small 11x11 house" means 11x11.
        Optional<int[]> explicit = DescriptionParser.dimensions(description);
        if (explicit.isPresent()) {
            int[] dims = explicit.get();
            width = dims[0];
            depth = dims[1];
            if (dims.length > 2) {
                height = dims[2];
            }
        }
        if (width < MIN_SIDE || depth < MIN_SIDE || width > MAX_SIDE || depth > MAX_SIDE
                || height < MIN_HEIGHT || height > MAX_HEIGHT) {
            return Optional.empty();
        }

        String material = DescriptionParser.material(description, "oak_planks").orElse("oak_planks");
        return Optional.of(new TemplateRequest(
                base.get(), width, depth, height, material,
                DescriptionParser.direction(description, facing), description));
    }

    @Override
    public List<SetBlockCommand> generate(TemplateRequest request) {
        List<SetBlockCommand> plan = new ArrayList<>();
        BlockPos base = request.base();
        int x0 = base.getX();
        int y0 = base.getY();
        int z0 = base.getZ();
        int width = request.width();
        int depth = request.depth();
        int height = request.height();
        String material = request.material();

        // Floor at the base layer, so the ground check compares against the terrain the owner meant.
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                plan.add(new SetBlockCommand(x0 + dx, y0, z0 + dz, material));
            }
        }

        // Worked out before the walls go up so an opening is never built and then knocked through:
        // placing a block costs an item, and overwriting it with air throws that item away.
        Set<BlockPos> openings = new LinkedHashSet<>();
        addWindows(openings, request);
        addDoorway(openings, request);

        // Hollow walls. The interior is left alone: filling it would both cost materials and bury
        // whatever the owner is standing on.
        for (int dy = 1; dy <= height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                wall(plan, openings, x0 + dx, y0 + dy, z0, material);
                wall(plan, openings, x0 + dx, y0 + dy, z0 + depth - 1, material);
            }
            for (int dz = 1; dz < depth - 1; dz++) {
                wall(plan, openings, x0, y0 + dy, z0 + dz, material);
                wall(plan, openings, x0 + width - 1, y0 + dy, z0 + dz, material);
            }
        }

        // Flat roof of full blocks. Stairs would give a nicer pitch but always face north from their
        // default state, so the result would look wrong in three orientations out of four.
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                plan.add(new SetBlockCommand(x0 + dx, y0 + height + 1, z0 + dz, material));
            }
        }

        // Openings are still written as air, not merely skipped: the house may be cut into a hillside,
        // where leaving the wall block out would leave the doorway plugged with terrain.
        for (BlockPos opening : openings) {
            plan.add(new SetBlockCommand(opening.getX(), opening.getY(), opening.getZ(), "air"));
        }
        addTorches(plan, request);
        return plan;
    }

    /** Places a wall block unless that position is a door or window. */
    private void wall(List<SetBlockCommand> plan, Set<BlockPos> openings, int x, int y, int z,
            String material) {
        if (!openings.contains(new BlockPos(x, y, z))) {
            plan.add(new SetBlockCommand(x, y, z, material));
        }
    }

    /**
     * A 1x2 gap in the middle of the wall the house faces. No door item: a door placed from its
     * default state is only the lower half and pops off as an item on the next block update.
     */
    private void addDoorway(Set<BlockPos> openings, TemplateRequest request) {
        BlockPos base = request.base();
        int x0 = base.getX();
        int y0 = base.getY();
        int z0 = base.getZ();
        int width = request.width();
        int depth = request.depth();

        int doorX;
        int doorZ;
        switch (request.facing()) {
            case SOUTH -> {
                doorX = x0 + width / 2;
                doorZ = z0 + depth - 1;
            }
            case EAST -> {
                doorX = x0 + width - 1;
                doorZ = z0 + depth / 2;
            }
            case WEST -> {
                doorX = x0;
                doorZ = z0 + depth / 2;
            }
            default -> {
                doorX = x0 + width / 2;
                doorZ = z0;
            }
        }
        openings.add(new BlockPos(doorX, y0 + 1, doorZ));
        openings.add(new BlockPos(doorX, y0 + 2, doorZ));
    }

    /**
     * Single-block gaps along each wall at eye height.
     *
     * <p>Open rather than glazed, for two reasons: glass has to be smelted, which is exactly the kind
     * of shortfall that stopped the logged build, and a one-block-tall gap is not walkable, so the
     * shelter stays sealed against anything that walks.
     */
    private void addWindows(Set<BlockPos> openings, TemplateRequest request) {
        BlockPos base = request.base();
        int x0 = base.getX();
        int z0 = base.getZ();
        int width = request.width();
        int depth = request.depth();
        int windowY = base.getY() + Math.max(2, request.height() - 1);

        for (int dx = 2; dx < width - 2; dx += 3) {
            openings.add(new BlockPos(x0 + dx, windowY, z0));
            openings.add(new BlockPos(x0 + dx, windowY, z0 + depth - 1));
        }
        for (int dz = 2; dz < depth - 2; dz += 3) {
            openings.add(new BlockPos(x0, windowY, z0 + dz));
            openings.add(new BlockPos(x0 + width - 1, windowY, z0 + dz));
        }
    }

    /** One torch per interior corner, standing on the floor that was just laid. */
    private void addTorches(List<SetBlockCommand> plan, TemplateRequest request) {
        BlockPos base = request.base();
        int x0 = base.getX();
        int y = base.getY() + 1;
        int z0 = base.getZ();
        int width = request.width();
        int depth = request.depth();

        plan.add(new SetBlockCommand(x0 + 1, y, z0 + 1, "torch"));
        plan.add(new SetBlockCommand(x0 + width - 2, y, z0 + 1, "torch"));
        plan.add(new SetBlockCommand(x0 + 1, y, z0 + depth - 2, "torch"));
        plan.add(new SetBlockCommand(x0 + width - 2, y, z0 + depth - 2, "torch"));
    }
}
