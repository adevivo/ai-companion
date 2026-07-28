package adris.altoclef.tasks.construction.build_structure.templates;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A structure request after the description has been parsed: everything a generator needs, with
 * defaults already applied.
 *
 * <p>{@code base} is the layer the lowest blocks occupy, matching what the DSL prompt calls
 * {@code baseY} and what {@code BuildPlacement} ground-checks against. {@code width} runs along X and
 * {@code depth} along Z, both measured in blocks including the walls. {@code height} is the number of
 * wall courses above the floor, so the roof of a house lands at {@code base.getY() + height + 1}.
 *
 * @param facing the direction the structure runs or faces. Doorways open onto this side.
 */
public record TemplateRequest(
        BlockPos base,
        int width,
        int depth,
        int height,
        String material,
        Direction facing,
        String description) {
}
