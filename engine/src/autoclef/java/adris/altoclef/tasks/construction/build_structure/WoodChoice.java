package adris.altoclef.tasks.construction.build_structure;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.construction.build_structure.StructureFromCode.SetBlockCommand;
import adris.altoclef.tasks.construction.build_structure.templates.DescriptionParser;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Picks which species of wood a build is actually made of, when the owner did not say.
 *
 * <p>Both plan sources hard-code oak. {@code DescriptionParser} maps every generic wood word —
 * "wood", "wooden", "plank", "planks", "log", "logs" — to {@code oak_planks} or {@code oak_log}, and
 * {@code HouseTemplate} falls back to {@code oak_planks} when nothing is named at all; the model
 * writing DSL does the same thing for the same reason. The result was a companion carrying 193 birch
 * logs, with no oak left anywhere, refusing to get on with a house until it was told in so many words
 * that birch would do.
 *
 * <p>So: when the description names no species, the plan is retyped to whatever the companion can
 * best supply out of its own pack. Naming one still wins outright — "a spruce house" builds in spruce
 * even with a chest full of birch, and fails honestly if there is no spruce, because that is a
 * request rather than a default.
 *
 * <p><b>Only ever applied to a freshly designed plan.</b> A remembered or resumed plan keeps the
 * species it started with: re-choosing halfway would leave a house half oak and half birch, and would
 * re-price cells that have already been paid for.
 */
public final class WoodChoice {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Species that follow the {@code <name>_planks} / {@code <name>_log} pattern, in the order used
     * to break ties — oak first, so a companion holding equal amounts of everything behaves exactly
     * as it did before this class existed.
     *
     * <p>Bamboo, crimson and warped are deliberately absent: their blocks are {@code bamboo_block},
     * {@code crimson_stem} and {@code warped_stem}, so the naming rule this relies on does not hold
     * and a rewrite would produce ids that do not exist.
     */
    private static final List<String> SPECIES = List.of(
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "cherry", "mangrove");

    /** How many planks one log crafts into. Vanilla's number, and the whole reason logs count here. */
    private static final int PLANKS_PER_LOG = 4;

    private WoodChoice() {
    }

    /**
     * The plan, retyped to the best-supplied wood species, or unchanged if there is no reason to.
     *
     * <p>Unchanged when: the description names a species, the plan uses no wood, or nothing in the
     * pack beats what the plan already asks for. That last case matters — with an empty inventory
     * every species scores zero, so the plan stays oak and the build goes and gathers oak exactly as
     * it always did.
     */
    public static List<SetBlockCommand> resolve(List<SetBlockCommand> plan, AltoClefController mod,
                                                String description) {
        if (plan == null || plan.isEmpty() || mod == null) {
            return plan;
        }
        if (DescriptionParser.namesWoodSpecies(description)) {
            return plan;
        }

        int planksNeeded = 0;
        int logsNeeded = 0;
        String current = null;
        for (SetBlockCommand command : plan) {
            String species = speciesOf(command.blockName, "_planks");
            if (species != null) {
                planksNeeded++;
                current = current == null ? species : current;
                continue;
            }
            species = speciesOf(command.blockName, "_log");
            if (species != null) {
                logsNeeded++;
                current = current == null ? species : current;
            }
        }
        if (current == null) {
            return plan; // a stone house, a dirt field: nothing here to retype
        }

        int currentCoverage = coverage(mod, current, planksNeeded, logsNeeded);
        String best = current;
        int bestCoverage = currentCoverage;
        for (String species : SPECIES) {
            if (species.equals(current)) {
                continue;
            }
            int score = coverage(mod, species, planksNeeded, logsNeeded);
            // Strictly greater, so a tie leaves the plan alone and SPECIES order stays meaningful.
            if (score > bestCoverage) {
                best = species;
                bestCoverage = score;
            }
        }
        if (best.equals(current)) {
            return plan;
        }

        LOGGER.info("Build ({}) retyped from {} to {}: covers {} of {} wood blocks against {}",
                description, current, best, bestCoverage, planksNeeded + logsNeeded, currentCoverage);

        List<SetBlockCommand> retyped = new ArrayList<>(plan.size());
        for (SetBlockCommand command : plan) {
            retyped.add(retype(command, best));
        }
        return retyped;
    }

    /**
     * How many of the plan's wood cells {@code species} could pay for out of the pack right now.
     *
     * <p>Logs are counted twice over, in the only order that is honest: first against the cells that
     * want logs, and then — whatever is left — as four planks each, because that is what crafting
     * them yields. Counting a log as both at once would claim a coverage the companion cannot deliver.
     */
    private static int coverage(AltoClefController mod, String species, int planksNeeded,
                                int logsNeeded) {
        int planks = count(mod, species + "_planks");
        int logs = count(mod, species + "_log");

        int logCells = Math.min(logsNeeded, logs);
        int spareLogs = logs - logCells;
        int plankCells = Math.min(planksNeeded, planks + spareLogs * PLANKS_PER_LOG);
        return logCells + plankCells;
    }

    /** How many of a block id the companion is carrying; 0 for anything unrecognised. */
    private static int count(AltoClefController mod, String blockId) {
        Block block = BuildMaterials.resolveBlock(blockId);
        if (block == Blocks.AIR) {
            return 0;
        }
        Item item = block.asItem();
        return item == Items.AIR ? 0 : mod.getItemStorage().getItemCount(item);
    }

    /**
     * The species prefix of {@code blockName} when it is exactly {@code <species><suffix>}, else null.
     *
     * <p>Exact matching against the known list on purpose: it means {@code stripped_oak_log} and
     * {@code oak_wood} are never touched. Neither is generated by the templates, but the model writing
     * DSL can emit anything, and silently retyping a block the plan deliberately chose would be worse
     * than leaving a mixed-species build alone.
     */
    private static String speciesOf(String blockName, String suffix) {
        if (blockName == null) {
            return null;
        }
        String id = blockName.trim().toLowerCase();
        int colon = id.indexOf(':');
        if (colon >= 0) {
            id = id.substring(colon + 1);
        }
        for (String species : SPECIES) {
            if (id.equals(species + suffix)) {
                return species;
            }
        }
        return null;
    }

    /** The same cell in {@code species}, or the cell untouched when it is not wood. */
    private static SetBlockCommand retype(SetBlockCommand command, String species) {
        if (speciesOf(command.blockName, "_planks") != null) {
            return new SetBlockCommand(command.x, command.y, command.z, species + "_planks");
        }
        if (speciesOf(command.blockName, "_log") != null) {
            return new SetBlockCommand(command.x, command.y, command.z, species + "_log");
        }
        return command;
    }
}
