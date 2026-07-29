package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.misc.FarmTask;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;

public class FarmCommand extends Command {
   private static final int DEFAULT_RANGE = 16;

   /**
    * Stands in for "no position given", so the centre falls back to wherever the companion is.
    *
    * <p>Cannot collide with a real coordinate: Y is bounded to roughly -64..320 and the world border
    * caps X/Z far short of this.
    */
   private static final int NO_COORD = Integer.MIN_VALUE;

   /**
    * All of x, y and z share this threshold so a centre is only taken when the full triple is present.
    *
    * <p>{@code ArgParser} uses an argument's default while {@code minArgCountToUseDefault >= givenArgs},
    * so at 3 these apply to `farm`, `farm <range>` and any partial coordinate, and only a complete
    * `farm <range> <x> <y> <z>` parses through to a real position.
    */
   private static final int NEEDS_FULL_TRIPLE = 3;

   public FarmCommand() throws CommandException {
      super(
         "farm",
         "Harvests and replants EXISTING nearby crops within range. This does NOT create a farm — it only tends"
            + " one that is already planted. To build a new field, use build_structure instead."
            + " Range is optional and defaults to 16. Example: `farm 10` to tend crops within 10 blocks."
            + " You can also give a field's coordinates to tend that field specifically: `farm 16 -7 62 360`."
            + " ALWAYS pass the coordinates when you have just built a field, otherwise you will tend whatever"
            + " happens to be near you instead of the field you built. All three of x, y and z are required"
            + " together. The range still applies around that point, so several fields close to it are all"
            + " tended in one pass.",
         // Optional: models routinely emit a bare `farm`, which used to hard-fail on a missing argument and
         // burn a whole LLM round-trip recovering. minArgCountToUseDefault=0 => applies when nothing is given.
         new Arg<>(Integer.class, "range", DEFAULT_RANGE, 0),
         new Arg<>(Integer.class, "x", NO_COORD, NEEDS_FULL_TRIPLE, false),
         new Arg<>(Integer.class, "y", NO_COORD, NEEDS_FULL_TRIPLE, false),
         new Arg<>(Integer.class, "z", NO_COORD, NEEDS_FULL_TRIPLE, false)
      );
   }

   @Override
   protected void call(AltoClefController controller, ArgParser parser) throws CommandException {
      Integer range = parser.get(Integer.class);
      Integer x = parser.get(Integer.class);
      Integer y = parser.get(Integer.class);
      Integer z = parser.get(Integer.class);
      // Without an explicit centre this is whatever block she is stood on, which is the field she was
      // asked about only if nothing moved her in the meantime. After a build, something does: the
      // LookAtOwnerTask that follows walks her to the owner while the next LLM turn is in flight.
      // Measured 2026-07-29 — a field built at (-7, 62, 360) ended up 37 blocks outside a `farm 16`
      // centred on (-9, 63, 323), so she tended an older field while reporting the new one as done.
      BlockPos origin = hasPosition(x, y, z)
         ? new BlockPos(x, y, z)
         : controller.getEntity().blockPosition();
      Task farmTask = new FarmTask(range, origin);
      controller.runUserTask(farmTask, () -> this.finish());
   }

   private static boolean hasPosition(Integer x, Integer y, Integer z) {
      return x != NO_COORD && y != NO_COORD && z != NO_COORD;
   }
}
