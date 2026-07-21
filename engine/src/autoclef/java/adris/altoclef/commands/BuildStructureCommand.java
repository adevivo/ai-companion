package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.construction.build_structure.BuildStructureTask;

public class BuildStructureCommand extends Command {
    public BuildStructureCommand() throws CommandException {
        super("build_structure",
                "Agent can build any thing in Minecraft given the description and position. The description should be a string generated to capture a clear and concise summary of the structure the user asked to be built. Agent does not need to collect materials to build the structure when using this function.\\n"
                        + //
                        "IMPORTANT: You must put a position into the description. If the player you are talking to doesn't give any hints on where to build it, put in that player's position into the description, or some positional information. You MUST give a coordiante to build at. If you don't know the player's position, then put your own position. \\n"
                        + //
                        " Example call would be `build_structure a gray modern house with a garden of roses in front of it. Build at position (-305, 406, 72)`",
                new Arg<>(String.class, "description"));
    }

    @Override
    protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
        String description = parser.get(String.class);
        mod.runUserTask(new BuildStructureTask(description, mod), () -> {
            this.finish();
        });
    }

}