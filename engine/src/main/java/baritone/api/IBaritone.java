package baritone.api;

import baritone.Baritone;
import baritone.api.behavior.ILookBehavior;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.cache.IWorldProvider;
import baritone.api.command.manager.ICommandManager;
import baritone.api.component.EntityComponentKey;
import baritone.api.event.listener.IEventBus;
import baritone.api.pathing.calc.IPathingControlManager;
import baritone.api.process.IBuilderProcess;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IExploreProcess;
import baritone.api.process.IFarmProcess;
import baritone.api.process.IFollowProcess;
import baritone.api.process.IGetToBlockProcess;
import baritone.api.process.IMineProcess;
import baritone.api.utils.IEntityContext;
import baritone.api.utils.IInputOverrideHandler;
import java.util.Arrays;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public interface IBaritone {
   EntityComponentKey<IBaritone> KEY = new EntityComponentKey<>(Baritone::new);

   IPathingBehavior getPathingBehavior();

   ILookBehavior getLookBehavior();

   IFollowProcess getFollowProcess();

   IMineProcess getMineProcess();

   IBuilderProcess getBuilderProcess();

   IExploreProcess getExploreProcess();

   IFarmProcess getFarmProcess();

   ICustomGoalProcess getCustomGoalProcess();

   IGetToBlockProcess getGetToBlockProcess();

   IWorldProvider getWorldProvider();

   IPathingControlManager getPathingControlManager();

   IInputOverrideHandler getInputOverrideHandler();

   IEntityContext getEntityContext();

   IEventBus getGameEventHandler();

   ICommandManager getCommandManager();

   void logDebug(String var1);

   default void logDirect(Component... components) {
      IEntityContext playerContext = this.getEntityContext();
      LivingEntity entity = playerContext.entity();
      if (entity instanceof Player) {
         MutableComponent component = Component.literal("");
         component.append(BaritoneAPI.getPrefix());
         component.append(Component.literal(" "));
         Arrays.asList(components).forEach(component::append);
         ((Player)entity).displayClientMessage(component, false);
      } else {
         // Route the companion's status/feedback to every player, not just creative-mode ones.
         // The old isCreative() filter meant survival players saw no command feedback at all.
         for (ServerPlayer p : entity.level().getServer().getPlayerList().getPlayers()) {
            MutableComponent component = Component.literal("");
            component.append(BaritoneAPI.getPrefix());
            component.append(Component.literal(" "));
            Arrays.asList(components).forEach(component::append);
            p.displayClientMessage(component, false);
         }
      }
   }

   default void logDirect(String message, ChatFormatting color) {
      Stream.of(message.split("\n")).forEach(line -> {
         MutableComponent component = Component.literal(line.replace("\t", "    "));
         component.setStyle(component.getStyle().applyFormat(color));
         this.logDirect(component);
      });
   }

   default void logDirect(String message) {
      this.logDirect(message, ChatFormatting.GRAY);
   }

   boolean isActive();

   Settings settings();

   void serverTick();
}
