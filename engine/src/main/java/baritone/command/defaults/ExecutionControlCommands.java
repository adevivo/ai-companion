package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.manager.ICommandManager;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class ExecutionControlCommands {
   private final Command pauseCommand = new Command("pause", "p") {
      @Override
      public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
         args.requireMax(0);
         ExecutionControlCommands.ExecControlProcess controlProcess = (ExecutionControlCommands.ExecControlProcess)((Baritone)baritone).getExecControlProcess();
         if (controlProcess.paused) {
            throw new CommandInvalidStateException("Already paused");
         } else {
            controlProcess.paused = true;
            this.logDirect(source, "Paused");
         }
      }

      @Override
      public Stream<String> tabComplete(String label, IArgConsumer args) {
         return Stream.empty();
      }

      @Override
      public String getShortDesc() {
         return "Pauses Automatone until you use resume";
      }

      @Override
      public List<String> getLongDesc() {
         return Arrays.asList(
            "The pause command tells Automatone to temporarily stop whatever it's doing.",
            "",
            "This can be used to pause pathing, building, following, whatever. A single use of the resume command will start it right back up again!",
            "",
            "Usage:",
            "> pause"
         );
      }
   };
   private final Command resumeCommand = new Command("resume", "r") {
      @Override
      public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
         args.requireMax(0);
         baritone.getBuilderProcess().resume();
         ExecutionControlCommands.ExecControlProcess controlProcess = (ExecutionControlCommands.ExecControlProcess)((Baritone)baritone).getExecControlProcess();
         if (!controlProcess.paused) {
            throw new CommandInvalidStateException("Not paused");
         } else {
            controlProcess.paused = false;
            this.logDirect(source, "Resumed");
         }
      }

      @Override
      public Stream<String> tabComplete(String label, IArgConsumer args) {
         return Stream.empty();
      }

      @Override
      public String getShortDesc() {
         return "Resumes Automatone processes after a pause";
      }

      @Override
      public List<String> getLongDesc() {
         return Arrays.asList("The resume command tells Automatone to resume whatever it was doing when you last used pause.", "", "Usage:", "> resume");
      }
   };
   private final Command pausedCommand = new Command("paused") {
      @Override
      public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
         args.requireMax(0);
         boolean paused = ((ExecutionControlCommands.ExecControlProcess)((Baritone)baritone).getExecControlProcess()).paused;
         this.logDirect(source, String.format("Automatone is %spaused", paused ? "" : "not "));
      }

      @Override
      public Stream<String> tabComplete(String label, IArgConsumer args) {
         return Stream.empty();
      }

      @Override
      public String getShortDesc() {
         return "Tells you if Baritone is paused";
      }

      @Override
      public List<String> getLongDesc() {
         return Arrays.asList("The paused command tells you if Baritone is currently paused by use of the pause command.", "", "Usage:", "> paused");
      }
   };
   private final Command cancelCommand = new Command("cancel", "c", "stop") {
      @Override
      public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
         args.requireMax(0);
         ((ExecutionControlCommands.ExecControlProcess)((Baritone)baritone).getExecControlProcess()).paused = false;
         baritone.getPathingBehavior().cancelEverything();
         this.logDirect(source, "ok canceled");
      }

      @Override
      public Stream<String> tabComplete(String label, IArgConsumer args) {
         return Stream.empty();
      }

      @Override
      public String getShortDesc() {
         return "Cancel what Baritone is currently doing";
      }

      @Override
      public List<String> getLongDesc() {
         return Arrays.asList("The cancel command tells Automatone to stop whatever it's currently doing.", "", "Usage:", "> cancel");
      }
   };

   public void registerCommands() {
      ICommandManager.registry.register(this.pauseCommand);
      ICommandManager.registry.register(this.resumeCommand);
      ICommandManager.registry.register(this.pausedCommand);
      ICommandManager.registry.register(this.cancelCommand);
   }

   public IBaritoneProcess registerProcess(IBaritone baritone) {
      ExecutionControlCommands.ExecControlProcess proc = new ExecutionControlCommands.ExecControlProcess();
      baritone.getPathingControlManager().registerProcess(proc);
      return proc;
   }

   private static class ExecControlProcess implements IBaritoneProcess {
      boolean paused;

      @Override
      public boolean isActive() {
         return this.paused;
      }

      @Override
      public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
         return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
      }

      @Override
      public boolean isTemporary() {
         return true;
      }

      @Override
      public void onLostControl() {
      }

      @Override
      public double priority() {
         return 0.0;
      }

      @Override
      public String displayName0() {
         return "Pause/Resume Commands";
      }
   }
}
