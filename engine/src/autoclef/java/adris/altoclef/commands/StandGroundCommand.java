package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;

/**
 * Suppress the retreat instinct for a while, then let it come back on its own.
 *
 * <p>This is the model's seam into fight-or-flight, and it is deliberately a <em>pre-commitment</em>
 * rather than a reaction. The companion decides whether to run inside {@code MobDefenseChain}, every
 * tick, from health and gear and how many things are hunting it — an LLM round trip is seconds, and a
 * companion that has to ask before defending itself is dead before the answer arrives. What the model
 * can usefully contribute is intent the survival logic has no way to know: that this doorway is worth
 * holding while its owner gets clear, that the thing behind it matters more than its own skin.
 *
 * <p>The timeout is not a detail. An override with no expiry is how a companion ends up dying for a
 * fight nobody remembers starting, so this always runs out and the instinct returns without anyone
 * having to remember to switch it off.
 *
 * <p>Note the cornered case is <em>not</em> this: a retreat that is making no headway converts to a
 * fight on its own, deterministically, because two seconds of getting nowhere while something hits you
 * is not ambiguous enough to need an opinion.
 */
public class StandGroundCommand extends Command {

   private static final int DEFAULT_SECONDS = 30;
   private static final int MAX_SECONDS = 300;

   public StandGroundCommand() throws CommandException {
      super(
         "stand_ground",
         "Stop retreating and fight, even when losing, for a number of seconds (default 30, max 300)."
            + " Use it when running is worse than fighting: holding a doorway so your owner can get"
            + " away, protecting something, or buying time. It expires by itself and normal"
            + " self-preservation comes back. You do NOT need this when merely cornered — being unable"
            + " to escape already makes you fight.",
         new Arg<>(Integer.class, "seconds", DEFAULT_SECONDS, 0)
      );
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
      int seconds = parser.get(Integer.class);
      if (seconds <= 0) {
         seconds = DEFAULT_SECONDS;
      }
      seconds = Math.min(seconds, MAX_SECONDS);

      mod.getMobDefenseChain().standGroundFor(mod, seconds);
      mod.log("Standing ground for " + seconds + "s — will not retreat until that runs out.");
      this.finish();
   }
}
