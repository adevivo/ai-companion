package baritone.api.event.listener;

import baritone.api.event.events.BlockInteractEvent;
import baritone.api.event.events.PathEvent;

public interface IGameEventListener {
   void onTickServer();

   void onBlockInteract(BlockInteractEvent var1);

   void onPathEvent(PathEvent var1);
}
