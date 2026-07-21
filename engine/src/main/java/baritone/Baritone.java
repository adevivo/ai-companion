package baritone;

import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.cache.IWorldProvider;
import baritone.api.event.listener.IEventBus;
import baritone.api.process.IBaritoneProcess;
import baritone.api.utils.IEntityContext;
import baritone.autoclef.AltoClefSettings;
import baritone.behavior.Behavior;
import baritone.behavior.InventoryBehavior;
import baritone.behavior.LookBehavior;
import baritone.behavior.MemoryBehavior;
import baritone.behavior.PathingBehavior;
import baritone.cache.WorldProvider;
import baritone.command.defaults.DefaultCommands;
import baritone.command.manager.BaritoneCommandManager;
import baritone.event.GameEventHandler;
import baritone.process.BackfillProcess;
import baritone.process.BuilderProcess;
import baritone.process.CustomGoalProcess;
import baritone.process.ExploreProcess;
import baritone.process.FarmProcess;
import baritone.process.FishingProcess;
import baritone.process.FollowProcess;
import baritone.process.GetToBlockProcess;
import baritone.process.MineProcess;
import baritone.utils.BlockStateInterface;
import baritone.utils.InputOverrideHandler;
import baritone.utils.PathingControlManager;
import baritone.utils.player.EntityContext;
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
   public AltoClefSettings altoClefSettings = new AltoClefSettings();

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

   public AltoClefSettings getExtraBaritoneSettings() {
      return this.altoClefSettings;
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
