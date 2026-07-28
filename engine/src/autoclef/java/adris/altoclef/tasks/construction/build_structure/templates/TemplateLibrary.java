package adris.altoclef.tasks.construction.build_structure.templates;

import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.construction.build_structure.StructureFromCode.SetBlockCommand;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;

/**
 * Matches a {@code build_structure} description against the built-in generators, so ordinary shapes
 * never reach the language model.
 *
 * <p>The DSL codegen prompt is ~4.2k tokens on a round-trip of its own, spent every single build, and
 * a plain rectangular house does not need designing. A template hit skips that entirely; a miss falls
 * through to exactly the path that ran before, unchanged.
 *
 * <p>Matching is intentionally reluctant. A miss costs one round-trip. A wrong match spends the
 * companion's materials building the wrong structure and then reports success, which is far worse and
 * much harder to notice.
 */
public final class TemplateLibrary {

    private static final Logger LOGGER = LogManager.getLogger();

    /** First confident match wins, so the more specific shapes are listed first. */
    private static final List<StructureTemplate> TEMPLATES =
            List.of(new FieldTemplate(), new HouseTemplate(), new LineTemplate());

    private TemplateLibrary() {
    }

    /** A matched template and the blocks it wants placed. */
    public record Match(String template, List<SetBlockCommand> plan) {
    }

    /**
     * The plan for {@code description}, or empty to fall through to DSL codegen.
     *
     * <p>Pure with respect to the world: the companion is read only for the direction it is facing,
     * used when the description does not name one.
     */
    public static Optional<Match> plan(String description, AltoClefController mod) {
        Optional<String> blocked = DescriptionParser.disqualifier(description);
        if (blocked.isPresent()) {
            // Logged rather than silent: the deny-list is the one part of this that has to be tuned
            // against what people actually ask for, and it can only be tuned from real declines.
            LOGGER.info("Build ({}) not templated: description mentions '{}'", description, blocked.get());
            return Optional.empty();
        }
        Direction facing = facing(mod);
        for (StructureTemplate template : TEMPLATES) {
            Optional<TemplateRequest> request = template.parse(description, facing);
            if (request.isEmpty()) {
                continue;
            }
            List<SetBlockCommand> plan = template.generate(request.get());
            if (plan.isEmpty()) {
                LOGGER.warn("Template {} matched ({}) but generated nothing; falling back to codegen",
                        template.name(), description);
                return Optional.empty();
            }
            LOGGER.info("Build ({}) matched template {}: {} blocks, {}x{}x{} of {}",
                    description, template.name(), plan.size(), request.get().width(),
                    request.get().depth(), request.get().height(), request.get().material());
            return Optional.of(new Match(template.name(), plan));
        }
        return Optional.empty();
    }

    /** Where the companion is looking, flattened to a compass direction. */
    private static Direction facing(AltoClefController mod) {
        LivingEntity entity = mod == null ? null : mod.getEntity();
        return entity == null ? Direction.NORTH : Direction.fromYRot(entity.getYRot());
    }
}
