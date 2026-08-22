package com.player2.playerengine.commands.random;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.base.Task;

public class DummyTaskCommand extends Command {
   public DummyTaskCommand() {
      super("dummy", "Doesnt do anything");
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      mod.runUserTask(new DummyTaskCommand.DummyTask(), () -> this.finish());
   }

   private class DummyTask extends Task {
      @Override
      protected void onStart() {
      }

      @Override
      protected Task onTick() {
         return null;
      }

      @Override
      protected void onStop(Task interruptTask) {
      }

      @Override
      protected boolean isEqual(Task other) {
         return false;
      }

      @Override
      protected String toDebugString() {
         return null;
      }
   }
}
