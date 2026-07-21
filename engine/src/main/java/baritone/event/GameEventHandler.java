package baritone.event;

import baritone.PlayerEngine;
import baritone.Baritone;
import baritone.api.event.events.BlockInteractEvent;
import baritone.api.event.events.PathEvent;
import baritone.api.event.listener.IEventBus;
import baritone.api.event.listener.IGameEventListener;
import baritone.utils.BlockStateInterface;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class GameEventHandler implements IEventBus {
   private final Baritone baritone;
   private final List<IGameEventListener> listeners = new CopyOnWriteArrayList<>();

   public GameEventHandler(Baritone baritone) {
      this.baritone = baritone;
   }

   @Override
   public void onTickServer() {
      try {
         this.baritone.bsi = new BlockStateInterface(this.baritone.getEntityContext());
      } catch (Exception var2) {
         PlayerEngine.LOGGER.error(var2);
         this.baritone.bsi = null;
      }

      this.listeners.forEach(IGameEventListener::onTickServer);
   }

   @Override
   public void onBlockInteract(BlockInteractEvent event) {
      this.listeners.forEach(l -> l.onBlockInteract(event));
   }

   @Override
   public void onPathEvent(PathEvent event) {
      this.listeners.forEach(l -> l.onPathEvent(event));
   }

   @Override
   public final void registerEventListener(IGameEventListener listener) {
      this.listeners.add(listener);
   }
}
