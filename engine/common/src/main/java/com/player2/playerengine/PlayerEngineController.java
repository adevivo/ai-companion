package com.player2.playerengine;

import com.player2.playerengine.automaton.AdditionalBaritoneSettings;
import com.player2.playerengine.chains.FoodChain;
import com.player2.playerengine.chains.AutoEquipArmorChain;
import com.player2.playerengine.chains.ScavengeFoodChain;
import com.player2.playerengine.chains.MLGBucketFallChain;
import com.player2.playerengine.chains.MobDefenseChain;
import com.player2.playerengine.chains.PlayerDefenseChain;
import com.player2.playerengine.chains.PlayerInteractionFixChain;
import com.player2.playerengine.chains.PreEquipItemChain;
import com.player2.playerengine.chains.UnstuckChain;
import com.player2.playerengine.chains.UserTaskChain;
import com.player2.playerengine.chains.WorldSurvivalChain;
import com.player2.playerengine.commands.BlockScanner;
import com.player2.playerengine.commands.base.CommandExecutor;
import com.player2.playerengine.control.InputControls;
import com.player2.playerengine.control.PlayerExtraController;
import com.player2.playerengine.control.SlotHandler;

import com.player2.playerengine.player2api.AgentConversationData;
import com.player2.playerengine.player2api.manager.ConversationManager;
import com.player2.playerengine.player2api.AIPersistantData;
import com.player2.playerengine.player2api.Player2APIService;

import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.base.TaskRunner;
import com.player2.playerengine.trackers.CraftingRecipeTracker;
import com.player2.playerengine.trackers.EntityStuckTracker;
import com.player2.playerengine.trackers.EntityTracker;
import com.player2.playerengine.trackers.MiscBlockTracker;
import com.player2.playerengine.trackers.SimpleChunkTracker;
import com.player2.playerengine.trackers.TrackerManager;
import com.player2.playerengine.trackers.UserBlockRangeTracker;
import com.player2.playerengine.trackers.storage.ContainerSubTracker;
import com.player2.playerengine.trackers.storage.ItemStorageTracker;
import com.player2.playerengine.util.time.ServerClock;
import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.component.BaritoneComponents;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import com.player2.playerengine.automaton.api.utils.IInteractionController;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.Playground;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

public class PlayerEngineController {
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
   private PlayerEngineSettings settings;
   private boolean paused = false;
   private Task storedTask;
   public boolean isStopping = false;
   private Player owner;

   public PlayerEngineController(IBaritone baritone, Character character, String player2GameId) {
      this.baritone = baritone;
      this.ctx = baritone.getEntityContext();
      // Before anything below constructs a TimerGame. Attaching here rather than relying solely on
      // SERVER_STARTED means we do not care whether this class was loaded before or after that event
      // fired — a controller only ever exists while a server is running.
      ServerClock.attach(this.ctx.world().getServer());
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
      // Below UserTaskChain, so picking up dropped food fills the gap after a job rather than
      // interrupting one. See ScavengeFoodChain.
      new ScavengeFoodChain(this.taskRunner);
      // Never owns the task slot; just puts on better armour when it finds some. See the class.
      new AutoEquipArmorChain(this.taskRunner);
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
      PlayerEngineSettings.load(
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

      // Build the memory index in the background, once per server. Done here rather than at mod
      // init so a player who never spawns a companion never touches the embedder at all. The call
      // is idempotent and returns immediately, so running it per companion costs nothing.
      com.player2.playerengine.player2api.CompanionMemory.warm(this.getWorld());
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
      TickEvent.SERVER_POST.register(PlayerEngineController::staticServerTick);
      // Everything the tick hook above touches is static and would otherwise survive into the next
      // world loaded in this game process — see ConversationManager.onServerStopping() and
      // BaritoneComponents.clearAll().
      LifecycleEvent.SERVER_STOPPING.register(server -> {
         ConversationManager.onServerStopping();
         BaritoneComponents.clearAll();
         ServerClock.detach();
      });
   }

   public static void staticServerTick(MinecraftServer server) {
      ServerClock.attach(server);
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
         PlayerEngineCommands.init(this);
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

   public com.player2.playerengine.automaton.api.Settings getBaritoneSettings() {
      return this.baritone.settings();
   }

   public AdditionalBaritoneSettings getExtraBaritoneSettings() {
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

   public PlayerEngineSettings getModSettings() {
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

   /**
    * Where this companion's thinking happens, and whose key and memories it uses.
    *
    * <p>Local for now — the game server does the work, as it always has. The seam exists so that
    * moving it to the owning client is a change of implementation rather than a change to the
    * conversation loop. Per companion rather than static, because the whole point of the move is
    * that two companions with two different owners must not share a credential.
    */
   private final com.player2.playerengine.player2api.brain.BrainTransport brainTransport =
         new com.player2.playerengine.player2api.brain.NetworkBrainTransport(
               this, new com.player2.playerengine.player2api.brain.LocalBrainTransport(this));

   /** @see com.player2.playerengine.player2api.brain.BrainTransport */
   public com.player2.playerengine.player2api.brain.BrainTransport getBrainTransport() {
      return this.brainTransport;
   }

   /** Puts a line in the owner's chat, so failures are visible in-game and not only in the log. */
   public void tellOwner(String message) {
      tellOwner(message, true);
   }

   /**
    * As above, for a notice that is not bad news.
    *
    * <p>Everything here used to be red, which is right for a failure and wrong for the message that
    * says the failure is over — a green "memory is back" is the confirmation someone gets after going
    * away to fix something, and rendering it in the same red as the complaint reads as a second
    * complaint.
    *
    * @param problem whether this reports something broken, rather than something recovered
    */
   public void tellOwner(String message, boolean problem) {
      if (message == null || message.isBlank()) {
         return;
      }
      try {
         if (this.owner instanceof ServerPlayer serverOwner) {
            serverOwner.sendSystemMessage(Component.literal(message)
                  .withStyle(problem ? ChatFormatting.RED : ChatFormatting.GREEN));
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
      UUID ownerUuid = getOwnerUuid();
      return ownerUuid != null && ownerUuid.equals(playerToCheck);
   }

   /**
    * Who this companion belongs to, or null.
    *
    * <p>Null-safe where {@code getOwner().getUUID()} was not, because this is now consulted on the
    * chat path for every companion on the server: one restored from a save whose owner is offline
    * has no {@code owner} reference until the brain re-attaches, and asking "is this message yours"
    * has to answer no rather than throw.
    */
   public UUID getOwnerUuid() {
      Player o = getOwner();
      return o == null ? null : o.getUUID();
   }

   public AIPersistantData getAIPersistantData() {
      return this.aiPersistantData;
   }

   public Player2APIService getPlayer2APIService() {
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
