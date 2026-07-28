package adris.altoclef.tasks.construction.build_structure.templates;

import java.util.List;
import java.util.Optional;

import adris.altoclef.tasks.construction.build_structure.StructureFromCode.SetBlockCommand;
import net.minecraft.core.Direction;

/**
 * A parametric structure generator: the built-in alternative to asking an LLM to write a DSL program
 * for a shape that does not need designing.
 *
 * <p>Generators emit the same {@link SetBlockCommand} list the DSL interpreter produces, so material
 * billing, the ground check and the already-correct-block skip in {@code BuildStructureTask} all apply
 * unchanged.
 *
 * <p><b>Full blocks only.</b> Placement writes {@code block.defaultBlockState()}, which has no facing
 * or half, so a door would place as a broken lower half and stairs would all face north. Doorways are
 * air gaps, roofs are full blocks, and torches only ever stand on top of something solid.
 */
public interface StructureTemplate {

    /** Short name for logs, e.g. {@code house}. */
    String name();

    /**
     * Whether this template can serve the description, and with what parameters.
     *
     * <p>Implementations must be conservative: an empty result costs a DSL round-trip, but a wrong
     * match silently builds the wrong thing and charges the companion for it. Decline anything
     * outside a plain rectangular footprint.
     *
     * @param description the raw {@code build_structure} description
     * @param facing fallback direction, from where the companion is looking
     */
    Optional<TemplateRequest> parse(String description, Direction facing);

    /** Expands a parsed request into block placements. Pure — no world access. */
    List<SetBlockCommand> generate(TemplateRequest request);
}
