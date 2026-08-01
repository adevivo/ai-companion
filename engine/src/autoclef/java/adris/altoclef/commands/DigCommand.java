package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.movement.DigStaircaseTask;
import adris.altoclef.tasksystem.Task;
import java.util.Locale;

/**
 * Cut a walkable staircase down at 45 degrees.
 *
 * <p>Named {@code dig} because that is the word both owners and models reach for. Three sessions
 * running, the model tried {@code dig down}, then {@code build_structure a descending staircase}
 * (which places blocks rather than removing them), then the name of the skill itself — each time
 * because nothing in the command list sounded like excavation. The behaviour is a staircase rather
 * than a vertical shaft, which is what "dig down" ought to mean in Minecraft anyway: a straight drop
 * is a hole you cannot climb out of and the usual way to land in lava.
 */
public class DigCommand extends Command {

    private static final int DEFAULT_DEPTH = 30;

    /** Stands in for "no direction given" — fall back to the way the companion is already facing. */
    private static final String NO_DIRECTION = "";

    public DigCommand() throws CommandException {
        super(
            "dig",
            "Cut a staircase DOWN at 45 degrees — one block down per block along — that you can walk back"
                + " up. This is how you excavate: there is no separate mining action, and build_structure"
                + " is the opposite (it places blocks and costs materials). Direction is north, south, east"
                + " or west and may be omitted to use the way you are facing. Depth is how many blocks BELOW"
                + " your current position to finish, default 30. Examples: `dig east 30` cuts east and ends"
                + " 30 blocks lower; `dig 15` does the same 15 blocks down in the direction you face. It"
                + " stops on its own at lava, at water, or near bedrock and tells you where it got to.",
            new Arg<>(String.class, "direction", NO_DIRECTION, 0, false),
            new Arg<>(Integer.class, "depth", DEFAULT_DEPTH, 1)
        );
    }

    @Override
    protected void call(AltoClefController controller, ArgParser parser) throws CommandException {
        String direction = parser.get(String.class);
        Integer depth = parser.get(Integer.class);

        // `dig 30` puts the depth in the direction slot, because the first argument is whatever was
        // typed first. Read a numeric direction as the depth it plainly is rather than rejecting it —
        // omitting the direction is the documented shorthand, so this is the form to expect most.
        Integer directionAsDepth = tryParseInt(direction);
        if (directionAsDepth != null) {
            depth = directionAsDepth;
            direction = NO_DIRECTION;
        }

        if (depth == null || depth <= 0) {
            throw new CommandException("Depth must be a positive number of blocks, e.g. `dig east 30`.");
        }

        String name = direction == null ? NO_DIRECTION : direction.trim().toLowerCase(Locale.ROOT);
        int dx;
        int dz;
        switch (name) {
            case "north" -> { dx = 0; dz = -1; }
            case "south" -> { dx = 0; dz = 1; }
            case "east" -> { dx = 1; dz = 0; }
            case "west" -> { dx = -1; dz = 0; }
            case NO_DIRECTION -> {
                // Whichever way it is already looking, snapped to a compass point. Yaw 0 faces +Z.
                float yaw = controller.getEntity().getYRot();
                int quadrant = Math.floorMod(Math.round(yaw / 90f), 4);
                switch (quadrant) {
                    case 0 -> { dx = 0; dz = 1; name = "south"; }
                    case 1 -> { dx = -1; dz = 0; name = "west"; }
                    case 2 -> { dx = 0; dz = -1; name = "north"; }
                    default -> { dx = 1; dz = 0; name = "east"; }
                }
            }
            default -> throw new CommandException("Direction must be north, south, east or west — got '"
                    + direction + "'. You can also leave it out to dig the way you are facing.");
        }

        Task task = new DigStaircaseTask(dx, dz, depth, name);
        controller.runUserTask(task, () -> this.finish());
    }

    private static Integer tryParseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
