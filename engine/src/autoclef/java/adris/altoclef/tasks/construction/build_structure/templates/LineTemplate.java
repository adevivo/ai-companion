package adris.altoclef.tasks.construction.build_structure.templates;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import adris.altoclef.tasks.construction.build_structure.StructureFromCode.SetBlockCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A straight run of blocks: a wall, a path, a road or a bridge.
 *
 * <p>All four are the same shape with different vertical extent and default material, so they share
 * one generator. The run starts at the given position and extends along {@code facing}, which comes
 * from a compass word in the description or, failing that, from where the companion is looking.
 */
public final class LineTemplate implements StructureTemplate {

    private static final List<String> WALL_KEYWORDS = List.of("wall", "barrier");
    private static final List<String> FLAT_KEYWORDS = List.of("path", "road", "bridge", "walkway");

    private static final int DEFAULT_LENGTH = 16;
    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 64;
    private static final int DEFAULT_WALL_HEIGHT = 3;
    private static final int MAX_WALL_HEIGHT = 8;
    private static final int MAX_WIDTH = 8;

    @Override
    public String name() {
        return "line";
    }

    @Override
    public Optional<TemplateRequest> parse(String description, Direction facing) {
        boolean wall = DescriptionParser.mentionsAny(description, WALL_KEYWORDS);
        boolean flat = DescriptionParser.mentionsAny(description, FLAT_KEYWORDS);
        if (!wall && !flat) {
            return Optional.empty();
        }
        Optional<BlockPos> base = DescriptionParser.position(description);
        if (base.isEmpty()) {
            return Optional.empty();
        }

        int length = DEFAULT_LENGTH;
        int width = 1;
        int height = wall ? DEFAULT_WALL_HEIGHT : 1;

        // "20x3" on a wall reads as length x height; on a path it reads as length x width. Anything
        // with a third number is a shape this generator does not have a meaning for.
        Optional<int[]> dims = DescriptionParser.dimensions(description);
        if (dims.isPresent()) {
            if (dims.get().length > 2) {
                return Optional.empty();
            }
            length = dims.get()[0];
            if (wall) {
                height = dims.get()[1];
            } else {
                width = dims.get()[1];
            }
        } else {
            OptionalInt stated = DescriptionParser.length(description);
            if (stated.isPresent()) {
                length = stated.getAsInt();
            }
        }
        if (length < MIN_LENGTH || length > MAX_LENGTH || width < 1 || width > MAX_WIDTH
                || height < 1 || height > MAX_WALL_HEIGHT) {
            return Optional.empty();
        }

        String material = DescriptionParser.material(description, wall ? "cobblestone" : "gravel")
                .orElse("cobblestone");
        return Optional.of(new TemplateRequest(
                base.get(), width, length, height, material,
                DescriptionParser.direction(description, facing), description));
    }

    @Override
    public List<SetBlockCommand> generate(TemplateRequest request) {
        List<SetBlockCommand> plan = new ArrayList<>();
        BlockPos base = request.base();
        Direction facing = request.facing();
        // Along the run, and across it. Both are horizontal, so the cross axis is the run rotated.
        Direction across = facing.getClockWise();

        int width = request.width();
        // Centre the run on the given position rather than hanging it off one side.
        int offset = -(width / 2);

        for (int step = 0; step < request.depth(); step++) {
            for (int lane = 0; lane < width; lane++) {
                for (int dy = 0; dy < request.height(); dy++) {
                    int x = base.getX() + facing.getStepX() * step + across.getStepX() * (offset + lane);
                    int z = base.getZ() + facing.getStepZ() * step + across.getStepZ() * (offset + lane);
                    plan.add(new SetBlockCommand(x, base.getY() + dy, z, request.material()));
                }
            }
        }
        return plan;
    }
}
