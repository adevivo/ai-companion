package baritone.behavior;

import baritone.Baritone;
import baritone.api.cache.IWaypoint;
import baritone.api.cache.Waypoint;
import baritone.api.event.events.BlockInteractEvent;
import baritone.api.utils.BetterBlockPos;
import baritone.utils.BlockStateInterface;
import net.minecraft.world.level.block.BedBlock;

public final class MemoryBehavior extends Behavior {
   public MemoryBehavior(Baritone baritone) {
      super(baritone);
   }

   @Override
   public void onBlockInteract(BlockInteractEvent event) {
      if (event.getType() == BlockInteractEvent.Type.USE && BlockStateInterface.getBlock(this.ctx, event.getPos()) instanceof BedBlock) {
         this.baritone
            .getWorldProvider()
            .getCurrentWorld()
            .getWaypoints()
            .addWaypoint(new Waypoint("bed", IWaypoint.Tag.BED, BetterBlockPos.from(event.getPos())));
      }
   }
}
