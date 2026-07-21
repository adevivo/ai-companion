package baritone.api.process;

import baritone.api.utils.BlockOptionalMeta;

public interface IGetToBlockProcess extends IBaritoneProcess {
   void getToBlock(BlockOptionalMeta var1);

   boolean blacklistClosest();
}
