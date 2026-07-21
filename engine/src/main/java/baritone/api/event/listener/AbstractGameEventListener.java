package baritone.api.event.listener;

import baritone.api.event.events.BlockInteractEvent;
import baritone.api.event.events.PathEvent;

public interface AbstractGameEventListener extends IGameEventListener {
   @Override
   default void onTickServer() {
   }

   @Override
   default void onBlockInteract(BlockInteractEvent event) {
   }

   @Override
   default void onPathEvent(PathEvent event) {
   }
}
