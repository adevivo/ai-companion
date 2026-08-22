package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.movement.GoToStrongholdPortalTask;
import com.player2.playerengine.tasks.movement.LocateDesertTempleTask;

public class LocateStructureCommand extends Command {
   public LocateStructureCommand() throws CommandException {
      super(
         "locate_structure",
         "Locate a world generated structure. Only works for stronghold and desert_temple",
         new Arg<>(LocateStructureCommand.Structure.class, "structure")
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      LocateStructureCommand.Structure structure = parser.get(LocateStructureCommand.Structure.class);
      switch (structure) {
         case STRONGHOLD:
            mod.runUserTask(new GoToStrongholdPortalTask(1), () -> this.finish());
            break;
         case DESERT_TEMPLE:
            mod.runUserTask(new LocateDesertTempleTask(), () -> this.finish());
      }
   }

   public static enum Structure {
      DESERT_TEMPLE,
      STRONGHOLD;
   }
}
