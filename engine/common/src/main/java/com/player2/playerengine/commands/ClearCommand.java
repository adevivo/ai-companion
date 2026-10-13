package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.construction.ClearAreaTask;
import net.minecraft.core.BlockPos;

/**
 * Remove every block in a box. The answer to "get rid of the dirt in the pen", which no other
 * command covers: {@code dig} only cuts staircases and {@code get} collects items without
 * clearing the spot they came from.
 */
public class ClearCommand extends Command {

    /** Keeps a mistyped coordinate from turning into a hillside-sized job. */
    static final int MAX_VOLUME = 512;

    public ClearCommand() throws CommandException {
        super(
            "clear",
            "Break and remove every block in the box from x1 y1 z1 to x2 y2 z2 (inclusive, max 512"
                + " blocks). Use this to break specific blocks, e.g. stray dirt in a pen: `clear 10 64 10"
                + " 14 64 12`. Skips chests/furnaces/beds etc., bedrock and fluids, and reports counts."
                + " `get` only collects items and will not clear a spot.",
            new Arg<>(Integer.class, "x1"), new Arg<>(Integer.class, "y1"), new Arg<>(Integer.class, "z1"),
            new Arg<>(Integer.class, "x2"), new Arg<>(Integer.class, "y2"), new Arg<>(Integer.class, "z2")
        );
    }

    @Override
    protected void call(PlayerEngineController controller, ArgParser parser) throws CommandException {
        int x1 = parser.get(Integer.class);
        int y1 = parser.get(Integer.class);
        int z1 = parser.get(Integer.class);
        int x2 = parser.get(Integer.class);
        int y2 = parser.get(Integer.class);
        int z2 = parser.get(Integer.class);

        long volume = (long) (Math.abs(x1 - x2) + 1) * (Math.abs(y1 - y2) + 1) * (Math.abs(z1 - z2) + 1);
        if (volume > MAX_VOLUME) {
            throw new CommandException("That box is " + volume + " blocks; clear handles at most "
                    + MAX_VOLUME + ". Use a smaller box, or several clear commands one after another.");
        }
        controller.runUserTask(new ClearAreaTask(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2)),
                () -> this.finish());
    }
}
