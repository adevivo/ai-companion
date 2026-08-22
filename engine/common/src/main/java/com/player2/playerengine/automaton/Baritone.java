/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.player2.playerengine.automaton;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.Settings;
import com.player2.playerengine.automaton.api.cache.IWorldProvider;
import com.player2.playerengine.automaton.api.event.listener.IEventBus;
import com.player2.playerengine.automaton.api.process.IBaritoneProcess;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import com.player2.playerengine.automaton.behavior.Behavior;
import com.player2.playerengine.automaton.behavior.InventoryBehavior;
import com.player2.playerengine.automaton.behavior.LookBehavior;
import com.player2.playerengine.automaton.behavior.MemoryBehavior;
import com.player2.playerengine.automaton.behavior.PathingBehavior;
import com.player2.playerengine.automaton.cache.WorldProvider;
import com.player2.playerengine.automaton.command.defaults.DefaultCommands;
import com.player2.playerengine.automaton.command.manager.BaritoneCommandManager;
import com.player2.playerengine.automaton.event.GameEventHandler;
import com.player2.playerengine.automaton.process.BackfillProcess;
import com.player2.playerengine.automaton.process.BuilderProcess;
import com.player2.playerengine.automaton.process.CustomGoalProcess;
import com.player2.playerengine.automaton.process.ExploreProcess;
import com.player2.playerengine.automaton.process.FarmProcess;
import com.player2.playerengine.automaton.process.FishingProcess;
import com.player2.playerengine.automaton.process.FollowProcess;
import com.player2.playerengine.automaton.process.GetToBlockProcess;
import com.player2.playerengine.automaton.process.MineProcess;
import com.player2.playerengine.automaton.utils.BlockStateInterface;
import com.player2.playerengine.automaton.utils.InputOverrideHandler;
import com.player2.playerengine.automaton.utils.PathingControlManager;
import com.player2.playerengine.automaton.utils.player.EntityContext;
import net.minecraft.world.entity.LivingEntity;

public class Baritone implements IBaritone {
   private final Settings settings;
   private final GameEventHandler gameEventHandler;
   private final PathingBehavior pathingBehavior;
   private final LookBehavior lookBehavior;
   private final MemoryBehavior memoryBehavior;
   private final InventoryBehavior inventoryBehavior;
   private final InputOverrideHandler inputOverrideHandler;
   private final FollowProcess followProcess;
   private final MineProcess mineProcess;
   private final GetToBlockProcess getToBlockProcess;
   private final CustomGoalProcess customGoalProcess;
   private final BuilderProcess builderProcess;
   private final ExploreProcess exploreProcess;
   private final BackfillProcess backfillProcess;
   private final FarmProcess farmProcess;
   private final FishingProcess fishingProcess;
   private final IBaritoneProcess execControlProcess;
   private final PathingControlManager pathingControlManager;
   private final BaritoneCommandManager commandManager;
   private final IEntityContext playerContext;
   public BlockStateInterface bsi;
   public AdditionalBaritoneSettings additionalBaritoneSettings = new AdditionalBaritoneSettings();

   public Baritone(LivingEntity player) {
      this.settings = new Settings();
      this.gameEventHandler = new GameEventHandler(this);
      this.playerContext = new EntityContext(player);
      this.pathingBehavior = new PathingBehavior(this);
      this.lookBehavior = new LookBehavior(this);
      this.memoryBehavior = new MemoryBehavior(this);
      this.inventoryBehavior = new InventoryBehavior(this);
      this.inputOverrideHandler = new InputOverrideHandler(this);
      this.pathingControlManager = new PathingControlManager(this);
      this.pathingControlManager.registerProcess(this.followProcess = new FollowProcess(this));
      this.pathingControlManager.registerProcess(this.mineProcess = new MineProcess(this));
      this.pathingControlManager.registerProcess(this.customGoalProcess = new CustomGoalProcess(this));
      this.pathingControlManager.registerProcess(this.getToBlockProcess = new GetToBlockProcess(this));
      this.pathingControlManager.registerProcess(this.builderProcess = new BuilderProcess(this));
      this.pathingControlManager.registerProcess(this.exploreProcess = new ExploreProcess(this));
      this.pathingControlManager.registerProcess(this.backfillProcess = new BackfillProcess(this));
      this.pathingControlManager.registerProcess(this.farmProcess = new FarmProcess(this));
      this.pathingControlManager.registerProcess(this.fishingProcess = new FishingProcess(this));
      this.commandManager = new BaritoneCommandManager(this);
      this.execControlProcess = DefaultCommands.controlCommands.registerProcess(this);
   }

   public PathingControlManager getPathingControlManager() {
      return this.pathingControlManager;
   }

   public void registerBehavior(Behavior behavior) {
      this.gameEventHandler.registerEventListener(behavior);
   }

   public InputOverrideHandler getInputOverrideHandler() {
      return this.inputOverrideHandler;
   }

   public CustomGoalProcess getCustomGoalProcess() {
      return this.customGoalProcess;
   }

   public GetToBlockProcess getGetToBlockProcess() {
      return this.getToBlockProcess;
   }

   @Override
   public IEntityContext getEntityContext() {
      return this.playerContext;
   }

   public MemoryBehavior getMemoryBehavior() {
      return this.memoryBehavior;
   }

   public FollowProcess getFollowProcess() {
      return this.followProcess;
   }

   public BuilderProcess getBuilderProcess() {
      return this.builderProcess;
   }

   public InventoryBehavior getInventoryBehavior() {
      return this.inventoryBehavior;
   }

   public LookBehavior getLookBehavior() {
      return this.lookBehavior;
   }

   public ExploreProcess getExploreProcess() {
      return this.exploreProcess;
   }

   public MineProcess getMineProcess() {
      return this.mineProcess;
   }

   public FarmProcess getFarmProcess() {
      return this.farmProcess;
   }

   public PathingBehavior getPathingBehavior() {
      return this.pathingBehavior;
   }

   public WorldProvider getWorldProvider() {
      return (WorldProvider)IWorldProvider.KEY.get(this.getEntityContext().world());
   }

   @Override
   public IEventBus getGameEventHandler() {
      return this.gameEventHandler;
   }

   public BaritoneCommandManager getCommandManager() {
      return this.commandManager;
   }

   public IBaritoneProcess getExecControlProcess() {
      return this.execControlProcess;
   }

   @Override
   public boolean isActive() {
      return this.pathingControlManager.isActive();
   }

   @Override
   public Settings settings() {
      return this.settings;
   }

   public AdditionalBaritoneSettings getExtraBaritoneSettings() {
      return this.additionalBaritoneSettings;
   }

   @Override
   public void logDebug(String message) {
      PlayerEngine.LOGGER.debug(message);
   }

   @Override
   public void serverTick() {
      this.getGameEventHandler().onTickServer();
   }

   public FishingProcess getFishingProcess() {
      return this.fishingProcess;
   }
}
