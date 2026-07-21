package baritone.api.pathing.path;

import baritone.api.pathing.calc.IPath;

public interface IPathExecutor {
   IPath getPath();

   int getPosition();
}
