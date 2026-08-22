package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.construction.build_structure.BuildStructureTask;

public class BuildStructureCommand extends Command {
    public BuildStructureCommand() throws CommandException {
        super("build_structure",
                "Agent can build any thing in Minecraft given the description and position. The description should be a string generated to capture a clear and concise summary of the structure the user asked to be built. Building COSTS MATERIALS out of your inventory — one item per block placed. If you are short, the build is refused and you are told what is missing, so `get` the materials first.\\n"
                        + //
                        "IMPORTANT: You must put a position into the description. If the player you are talking to doesn't give any hints on where to build it, put in that player's position into the description, or some positional information. You MUST give a coordiante to build at. If you don't know the player's position, then put your own position. \\n"
                        + //
                        " Example call would be `build_structure a gray modern house with a garden of roses in front of it. Build at position (-305, 406, 72)`",
                new Arg<>(String.class, "description"));
    }

    @Override
    protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
        String description = parser.get(String.class);
        mod.runUserTask(new BuildStructureTask(description, mod), () -> {
            this.finish();
        });
    }

}