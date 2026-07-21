package baritone.api.process;

import java.nio.file.Path;

public interface IExploreProcess extends IBaritoneProcess {
   void explore(int var1, int var2);

   void applyJsonFilter(Path var1, boolean var2) throws Exception;
}
