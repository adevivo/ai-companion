package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.movement.BodyLanguageTask;

public class BodyLanguageCommand extends Command {
    public BodyLanguageCommand() throws CommandException {
        super("bodylang",
                "Perform some sort of dance/body language action. Action must be either `greeting`, `nod_head`, `shake_head`, `victory` ",
                new Arg<>(String.class, "bodyLanguage"));
    }

    @Override
    protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
        String bodyLanguage = parser.get(String.class);
        mod.runUserTask(new BodyLanguageTask(bodyLanguage), () -> {
            System.out.println("Body language done");
            this.finish();
        });
    }

}