package baritone.api.pathing.calc;

import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import java.util.Optional;

public interface IPathingControlManager {
   void registerProcess(IBaritoneProcess var1);

   Optional<IBaritoneProcess> mostRecentInControl();

   Optional<PathingCommand> mostRecentCommand();
}
