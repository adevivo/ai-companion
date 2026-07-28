package adris.altoclef;

import adris.altoclef.chains.FoodChain;
import adris.altoclef.chains.MLGBucketFallChain;
import adris.altoclef.chains.MobDefenseChain;
import adris.altoclef.chains.PlayerDefenseChain;
import adris.altoclef.chains.PlayerInteractionFixChain;
import adris.altoclef.chains.PreEquipItemChain;
import adris.altoclef.chains.UnstuckChain;
import adris.altoclef.chains.UserTaskChain;
import adris.altoclef.chains.WorldSurvivalChain;
import adris.altoclef.commands.BlockScanner;
import adris.altoclef.commandsystem.CommandExecutor;
import adris.altoclef.control.InputControls;
import adris.altoclef.control.PlayerExtraController;
import adris.altoclef.control.SlotHandler;

import adris.altoclef.player2api.AgentConversationData;
import adris.altoclef.player2api.manager.ConversationManager;
import adris.altoclef.player2api.AIPersistantData;
import adris.altoclef.player2api.Player2APIService;

import adris.altoclef.player2api.Character;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.trackers.CraftingRecipeTracker;
import adris.altoclef.trackers.EntityStuckTracker;
import adris.altoclef.trackers.EntityTracker;
import adris.altoclef.trackers.MiscBlockTracker;
import adris.altoclef.trackers.SimpleChunkTracker;
import adris.altoclef.trackers.TrackerManager;
import adris.altoclef.trackers.UserBlockRangeTracker;
import adris.altoclef.trackers.storage.ContainerSubTracker;
import adris.altoclef.trackers.storage.ItemStorageTracker;
import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.component.BaritoneComponents;
import baritone.api.entity.LivingEntityInventory;
import baritone.api.utils.IEntityContext;
import baritone.api.utils.IInteractionController;
import baritone.autoclef.AltoClefSettings;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

public class AltoClefController {
   private final IBaritone baritone;
   private AIPersistantData aiPersistantData;
   private Player2APIService player2apiService;
   private final IEntityContext ctx;
   private CommandExecutor commandExecutor;
   private TaskRunner taskRunner;
   private TrackerManager trackerManager;
   private BotBehaviour botBehaviour;
   private UserTaskChain userTaskChain;
   private FoodChain foodChain;
   private MobDefenseChain mobDefenseChain;
   private MLGBucketFallChain mlgBucketChain;
   private ItemStorageTracker storageTracker;
   private ContainerSubTracker containerSubTracker;
   private EntityTracker entityTracker;
   private BlockScanner blockScanner;
   private SimpleChunkTracker chunkTracker;
   private MiscBlockTracker miscBlockTracker;
   private CraftingRecipeTracker craftingRecipeTracker;
   private EntityStuckTracker entityStuckTracker;
   private UserBlockRangeTracker userBlockRangeTracker;
   private InputControls inputControls;
   private SlotHandler slotHandler;
   private PlayerExtraController extraController;
   private Settings settings;
   private boolean paused = false;
   private Task storedTask;
   public boolean isStopping = false;
   private Player owner;

   public AltoClefController(IBaritone baritone, Character character, String player2GameId) {
      this.baritone = baritone;
      this.ctx = baritone.getEntityContext();
      this.commandExecutor = new CommandExecutor(this);
      this.taskRunner = new TaskRunner(this);
      this.trackerManager = new TrackerManager(this);
      this.userTaskChain = new UserTaskChain(this.taskRunner);
      this.mobDefenseChain = new MobDefenseChain(this.taskRunner);
      new PlayerInteractionFixChain(this.taskRunner);
      this.mlgBucketChain = new MLGBucketFallChain(this.taskRunner);
      new UnstuckChain(this.taskRunner);
      new PreEquipItemChain(this.taskRunner);
      new WorldSurvivalChain(this.taskRunner);
      this.foodChain = new FoodChain(this.taskRunner);
      new PlayerDefenseChain(this.taskRunner);
      this.storageTracker = new ItemStorageTracker(this, this.trackerManager,
            container -> this.containerSubTracker = container);
      this.entityTracker = new EntityTracker(this.trackerManager);
      this.blockScanner = new BlockScanner(this);
      this.chunkTracker = new SimpleChunkTracker(this);
      this.miscBlockTracker = new MiscBlockTracker(this);
      this.craftingRecipeTracker = new CraftingRecipeTracker(this.trackerManager);
      this.entityStuckTracker = new EntityStuckTracker(this.trackerManager);
      this.userBlockRangeTracker = new UserBlockRangeTracker(this.trackerManager);
      this.inputControls = new InputControls(this);
      this.slotHandler = new SlotHandler(this);
      this.extraController = new PlayerExtraController(this);
      this.initializeBaritoneSettings();
      this.botBehaviour = new BotBehaviour(this);
      this.initializeCommands();
      Settings.load(
            newSettings -> {
               this.settings = newSettings;
               List<Item> baritoneCanPlace = Arrays.stream(this.settings.getThrowawayItems(this, true)).toList();
               this.getBaritoneSettings().acceptableThrowawayItems.get().addAll(baritoneCanPlace);
               if ((!this.getUserTaskChain().isActive() || this.getUserTaskChain().isRunningIdleTask())
                     && this.getModSettings().shouldRunIdleCommandWhenNotActive()) {
                  this.getUserTaskChain().signalNextTaskToBeIdleTask();
                  this.getCommandExecutor().executeWithPrefix(this.getModSettings().getIdleCommand());
               }

               this.getExtraBaritoneSettings().avoidBlockBreak(this.userBlockRangeTracker::isNearUserTrackedBlock);
               this.getExtraBaritoneSettings().avoidBlockPlace(this.entityStuckTracker::isBlockedByEntity);
            });
      Playground.IDLE_TEST_INIT_FUNCTION(this);

      // AI setup: (should be at end to ensure as many things are not null as
      // possible)
      ConversationManager.getOrCreateEventQueueData(this);
      this.aiPersistantData = new AIPersistantData(this, character);
      this.player2apiService = new Player2APIService(this, player2GameId);
   }

   public void serverTick() {
      this.inputControls.onTickPre();
      this.storageTracker.setDirty();
      this.miscBlockTracker.tick();
      this.trackerManager.tick();
      this.blockScanner.tick();
      this.taskRunner.tick();
      this.inputControls.onTickPost();
      this.baritone.serverTick();
      this.player2apiService.trySendHeartbeat();
   }

   static {
      ServerTickEvents.END_SERVER_TICK.register(AltoClefController::staticServerTick);
      // Everything the tick hook above touches is static and would otherwise survive into the next
      // world loaded in this game process — see ConversationManager.onServerStopping() and
      // BaritoneComponents.clearAll().
      ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
         ConversationManager.onServerStopping();
         BaritoneComponents.clearAll();
      });
   }

   public static void staticServerTick(MinecraftServer server) {
      ConversationManager.injectOnTick(server);
   }

   public void stop() {
      this.getUserTaskChain().cancel(this);
      if (this.taskRunner.getCurrentTaskChain() != null) {
         this.taskRunner.getCurrentTaskChain().stop();
      }

      this.getTaskRunner().disable();
      this.getBaritone().getPathingBehavior().forceCancel();
      this.getBaritone().getInputOverrideHandler().clearAllKeys();
   }

   private void initializeBaritoneSettings() {
      this.getExtraBaritoneSettings().canWalkOnEndPortal(false);
      this.getExtraBaritoneSettings().avoidBlockPlace(this.entityStuckTracker::isBlockedByEntity);
      this.getExtraBaritoneSettings().avoidBlockBreak(this.userBlockRangeTracker::isNearUserTrackedBlock);
      this.getBaritoneSettings().freeLook.set(false);
      this.getBaritoneSettings().overshootTraverse.set(true);
      this.getBaritoneSettings().allowOvershootDiagonalDescend.set(true);
      this.getBaritoneSettings().allowInventory.set(true);
      this.getBaritoneSettings().allowParkour.set(false);
      this.getBaritoneSettings().allowParkourAscend.set(false);
      this.getBaritoneSettings().allowParkourPlace.set(false);
      this.getBaritoneSettings().allowDiagonalDescend.set(false);
      this.getBaritoneSettings().allowDiagonalAscend.set(false);
      this.getBaritoneSettings().fadePath.set(true);
      this.getBaritoneSettings().mineScanDroppedItems.set(false);
      this.getBaritoneSettings().mineDropLoiterDurationMSThanksLouca.set(0L);
      this.getExtraBaritoneSettings().configurePlaceBucketButDontFall(true);
      this.getBaritoneSettings().randomLooking.set(0.0);
      this.getBaritoneSettings().randomLooking113.set(0.0);
      this.getBaritoneSettings().failureTimeoutMS.reset();
      this.getBaritoneSettings().planAheadFailureTimeoutMS.reset();
      this.getBaritoneSettings().movementTimeoutTicks.reset();
   }

   private void initializeCommands() {
      try {
         AltoClefCommands.init(this);
      } catch (Exception var2) {
         var2.printStackTrace();
      }
   }

   public void runUserTask(Task task, Runnable onFinish) {
      this.userTaskChain.runTask(this, task, onFinish);
   }

   public void runUserTask(Task task) {
      this.runUserTask(task, () -> {
      });
   }

   public void cancelUserTask() {
      this.userTaskChain.cancel(this);
   }

   public CommandExecutor getCommandExecutor() {
      return this.commandExecutor;
   }

   public LivingEntity getEntity() {
      return this.ctx.entity();
   }

   public ServerLevel getWorld() {
      return this.ctx.world();
   }

   public IInteractionController getInteractionManager() {
      return this.ctx.playerController();
   }

   public IBaritone getBaritone() {
      return this.baritone;
   }

   public baritone.api.Settings getBaritoneSettings() {
      return this.baritone.settings();
   }

   public AltoClefSettings getExtraBaritoneSettings() {
      return ((Baritone) this.baritone).getExtraBaritoneSettings();
   }

   public TaskRunner getTaskRunner() {
      return this.taskRunner;
   }

   public UserTaskChain getUserTaskChain() {
      return this.userTaskChain;
   }

   public BotBehaviour getBehaviour() {
      return this.botBehaviour;
   }

   public boolean isPaused() {
      return this.paused;
   }

   public void setPaused(boolean pausing) {
      this.paused = pausing;
   }

   public Task getStoredTask() {
      return this.storedTask;
   }

   public void setStoredTask(Task currentTask) {
      this.storedTask = currentTask;
   }

   public ItemStorageTracker getItemStorage() {
      return this.storageTracker;
   }

   public EntityTracker getEntityTracker() {
      return this.entityTracker;
   }

   public CraftingRecipeTracker getCraftingRecipeTracker() {
      return this.craftingRecipeTracker;
   }

   public BlockScanner getBlockScanner() {
      return this.blockScanner;
   }

   public SimpleChunkTracker getChunkTracker() {
      return this.chunkTracker;
   }

   public MiscBlockTracker getMiscBlockTracker() {
      return this.miscBlockTracker;
   }

   public Settings getModSettings() {
      return this.settings;
   }

   public FoodChain getFoodChain() {
      return this.foodChain;
   }

   public MobDefenseChain getMobDefenseChain() {
      return this.mobDefenseChain;
   }

   public MLGBucketFallChain getMLGBucketChain() {
      return this.mlgBucketChain;
   }

   public void log(String message) {
      Debug.logMessage(message);
   }

   public void logWarning(String message) {
      Debug.logWarning(message);
   }

   /**
    * Report something that went wrong to the log, to the agent's next turn, and to the owner in chat.
    *
    * <p>For soft failures — a command that ran to completion without doing what was asked, like a
    * deposit with nothing to deposit, or a build that could not afford its materials. Those report as
    * "finished" to the task system, so without this the agent believes it succeeded and stands there
    * while the player wonders why nothing happened.
    */
   public void logAgentNotice(String message) {
      logAgentNotice(message, message);
   }

   /**
    * As {@link #logAgentNotice(String)}, with separate wording for the owner.
    *
    * <p>Agent-facing text carries instructions the model needs ("use `get` to collect them, then build
    * again") that read as noise in chat. Pass a plain sentence as {@code playerMessage} to tell the
    * owner what happened in their own terms, or null to keep it out of chat entirely.
    *
    * <p>The notice goes to the agent by two routes on purpose. {@code gameDebugMessages} is a rolling
    * buffer that {@code MessageBuffer.dumpAndGetString} <b>drains</b> as it reads, so anything left
    * only there is visible for exactly one turn and then gone — while the "finished running" event
    * queued alongside it stays in the conversation history forever. That asymmetry is how a build that
    * ran out of materials came to be reported to the owner as a finished house: by the following turn
    * the only surviving evidence said "finished". Recording it as a pending failure as well lets
    * {@code onCommandFinish} state the outcome in the event that does persist.
    */
   public void logAgentNotice(String message, String playerMessage) {
      logWarning(message);
      try {
         AgentConversationData data = ConversationManager.getOrCreateEventQueueData(this);
         data.addAltoclefLogMessage(message);
         data.recordCommandFailure(message);
      } catch (Exception e) {
         Debug.logWarning("Could not deliver notice to the agent: " + e);
      }
      tellOwner(playerMessage);
   }

   /**
    * Tell the agent something without claiming the running command failed.
    *
    * <p>For notices that are not about a command at all — a health warning raised from the entity
    * tick, say. Routing those through {@link #logAgentNotice} would leave a pending failure behind
    * that the next command to finish would wrongly report as its own outcome.
    */
   public void logAgentInfo(String message) {
      logWarning(message);
      try {
         ConversationManager.getOrCreateEventQueueData(this).addAltoclefLogMessage(message);
      } catch (Exception e) {
         Debug.logWarning("Could not deliver notice to the agent: " + e);
      }
   }

   /** Puts a line in the owner's chat, so failures are visible in-game and not only in the log. */
   public void tellOwner(String message) {
      if (message == null || message.isBlank()) {
         return;
      }
      try {
         if (this.owner instanceof ServerPlayer serverOwner) {
            serverOwner.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
         }
      } catch (Exception e) {
         Debug.logWarning("Could not deliver notice to the owner: " + e);
      }
   }

   public static boolean inGame() {
      return true;
   }

   public LivingEntity getPlayer() {
      return this.ctx.entity();
   }

   public InputControls getInputControls() {
      return this.inputControls;
   }

   public SlotHandler getSlotHandler() {
      return this.slotHandler;
   }

   public LivingEntityInventory getInventory() {
      return this.getBaritone().getEntityContext().inventory();
   }

   public PlayerExtraController getControllerExtras() {
      return this.extraController;
   }

   public void setChatClefEnabled(boolean enabled) {
      ConversationManager.getOrCreateEventQueueData(this).setEnabled(enabled);

      if (!enabled) {
         this.getUserTaskChain().cancel(this);
         this.getTaskRunner().disable();
      }
   }

   public void logCharacterMessage(String message, Character character, boolean isPublic) {
      int maxLength = 256;
      int start = 0;

      while (start < message.length()) {
         int end = Math.min(start + maxLength, message.length());
         String chunk = message.substring(start, end);
         if (chunk.length() > 0 && !chunk.isBlank()) {
            Debug.logCharacterMessage(chunk, character, isPublic);
         }

         start = end;
      }
   }

   public Player getOwner() {
      return this.owner;
   }

   public void setOwner(Player owner) {
      this.owner = owner;
      aiPersistantData.updateSystemPrompt();
   }

   public boolean isOwner(UUID playerToCheck) {
      return playerToCheck.equals(owner.getUUID());
   }

   public adris.altoclef.player2api.AIPersistantData getAIPersistantData() {
      return this.aiPersistantData;
   }

   public adris.altoclef.player2api.Player2APIService getPlayer2APIService() {
      return this.player2apiService;
   }

   public String getOwnerUsername() {
      if (getOwner() == null) {
         return "UNKNOWN OWNER";
      }
      return getOwner().getName().getString();
   }

   public Optional<ServerPlayer> getClosestPlayer() {
      return this.getWorld().players().stream().sorted((a, b) -> {
         float adist = a.distanceTo(this.getEntity());
         float bdist = b.distanceTo(this.getEntity());
         return Float.compare(adist, bdist);
      }).findFirst();
   }
}
