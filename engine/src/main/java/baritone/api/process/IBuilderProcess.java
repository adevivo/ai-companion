package baritone.api.process;

import baritone.api.schematic.ISchematic;
import baritone.utils.DirUtil;
import java.io.File;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

public interface IBuilderProcess extends IBaritoneProcess {
   void build(String var1, ISchematic var2, Vec3i var3);

   boolean build(String var1, File var2, Vec3i var3);

   default boolean build(String schematicFile, BlockPos origin) {
      File file = DirUtil.getGameDir().resolve("schematics").resolve(schematicFile).toFile();
      return this.build(schematicFile, file, origin);
   }

   void buildOpenSchematic();

   void pause();

   boolean isPaused();

   void resume();

   void clearArea(BlockPos var1, BlockPos var2);

   List<BlockState> getApproxPlaceable();
}
