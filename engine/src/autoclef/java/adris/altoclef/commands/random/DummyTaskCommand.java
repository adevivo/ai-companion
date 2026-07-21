package adris.altoclef.commands.random;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasksystem.Task;

public class DummyTaskCommand extends Command {
   public DummyTaskCommand() {
      super("dummy", "Doesnt do anything");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
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
