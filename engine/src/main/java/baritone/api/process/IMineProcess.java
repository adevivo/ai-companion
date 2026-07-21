package baritone.api.process;

import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BlockOptionalMetaLookup;
import net.minecraft.world.level.block.Block;

public interface IMineProcess extends IBaritoneProcess {
   void mineByName(int var1, String... var2);

   void mine(int var1, BlockOptionalMetaLookup var2);

   default void mine(BlockOptionalMetaLookup filter) {
      this.mine(0, filter);
   }

   default void mineByName(String... blocks) {
      this.mineByName(0, blocks);
   }

   default void mine(int quantity, BlockOptionalMeta... boms) {
      this.mine(quantity, new BlockOptionalMetaLookup(boms));
   }

   default void mine(BlockOptionalMeta... boms) {
      this.mine(0, boms);
   }

   void mine(int var1, Block... var2);

   default void mine(Block... blocks) {
      this.mine(0, blocks);
   }

   default void cancel() {
      this.onLostControl();
   }
}
