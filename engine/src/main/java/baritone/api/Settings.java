package baritone.api;

import baritone.api.utils.SettingsUtil;
import baritone.api.utils.TypeUtils;
import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

public final class Settings {
   public final Settings.Setting<Boolean> allowBreak = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> allowSprint = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> allowPlace = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> allowInventory = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> assumeExternalAutoTool = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> disableAutoTool = new Settings.Setting<>(false);
   public final Settings.Setting<Double> blockPlacementPenalty = new Settings.Setting<>(20.0);
   public final Settings.Setting<Double> blockBreakAdditionalPenalty = new Settings.Setting<>(2.0);
   public final Settings.Setting<Double> jumpPenalty = new Settings.Setting<>(2.0);
   public final Settings.Setting<Double> walkOnWaterOnePenalty = new Settings.Setting<>(3.0);
   public final Settings.Setting<Boolean> allowWaterBucketFall = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> allowSwimming = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> ignoreBreath = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> assumeWalkOnWater = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> assumeWalkOnLava = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> assumeStep = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> assumeSafeWalk = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> allowJumpAt256 = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> allowParkourAscend = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> allowDiagonalDescend = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> allowDiagonalAscend = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> allowDownward = new Settings.Setting<>(true);
   public final Settings.Setting<List<Item>> acceptableThrowawayItems = new Settings.Setting<>(
      new ArrayList<>(
         List.of(
            Blocks.DIRT.asItem(),
            Blocks.NETHERRACK.asItem(),
            Blocks.BASALT.asItem(),
            Blocks.STONE.asItem(),
            Blocks.BLACKSTONE.asItem(),
            Blocks.GRANITE.asItem(),
            Blocks.DIORITE.asItem(),
            Blocks.ANDESITE.asItem(),
            Blocks.SOUL_SOIL.asItem()
         )
      )
   );
   public final Settings.Setting<List<Block>> blocksToAvoid = new Settings.Setting<>(new ArrayList<>(List.of()));
   public final Settings.Setting<List<Block>> blocksToAvoidBreaking = new Settings.Setting<>(
      new ArrayList<>(List.of(Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.CAMPFIRE, Blocks.SMOKER, Blocks.BLAST_FURNACE, Blocks.CHEST, Blocks.TRAPPED_CHEST))
   );
   public final Settings.Setting<TagKey<Block>> buildIgnoreBlocks = new Settings.Setting<>(
      TagKey.create(Registries.BLOCK, new ResourceLocation("automatone", "build/ignored_blocks"))
   );
   public final Settings.Setting<TagKey<Block>> okIfAir = new Settings.Setting<>(
      TagKey.create(Registries.BLOCK, new ResourceLocation("automatone", "build/ok_if_air"))
   );
   public final Settings.Setting<Boolean> buildIgnoreExisting = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> avoidUpdatingFallingBlocks = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> allowWalkOnBottomSlab = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> allowParkour = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> allowParkourPlace = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> considerPotionEffects = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> sprintAscends = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> overshootTraverse = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> pauseMiningForFallingBlocks = new Settings.Setting<>(true);
   public final Settings.Setting<Integer> rightClickSpeed = new Settings.Setting<>(4);
   public final Settings.Setting<Double> randomLooking113 = new Settings.Setting<>(2.0);
   public final Settings.Setting<Double> randomLooking = new Settings.Setting<>(0.01);
   public final Settings.Setting<Double> costHeuristic = new Settings.Setting<>(3.563);
   public final Settings.Setting<Integer> pathingMaxChunkBorderFetch = new Settings.Setting<>(50);
   public final Settings.Setting<Double> backtrackCostFavoringCoefficient = new Settings.Setting<>(0.5);
   public final Settings.Setting<Boolean> avoidance = new Settings.Setting<>(false);
   public final Settings.Setting<Double> mobSpawnerAvoidanceCoefficient = new Settings.Setting<>(2.0);
   public final Settings.Setting<Integer> mobSpawnerAvoidanceRadius = new Settings.Setting<>(16);
   public final Settings.Setting<Double> mobAvoidanceCoefficient = new Settings.Setting<>(1.5);
   public final Settings.Setting<Integer> mobAvoidanceRadius = new Settings.Setting<>(8);
   public final Settings.Setting<Boolean> rightClickContainerOnArrival = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> enterPortal = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> minimumImprovementRepropagation = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> cutoffAtLoadBoundary = new Settings.Setting<>(false);
   public final Settings.Setting<Double> maxCostIncrease = new Settings.Setting<>(10.0);
   public final Settings.Setting<Integer> costVerificationLookahead = new Settings.Setting<>(5);
   public final Settings.Setting<Double> pathCutoffFactor = new Settings.Setting<>(0.9);
   public final Settings.Setting<Integer> pathCutoffMinimumLength = new Settings.Setting<>(30);
   public final Settings.Setting<Integer> planningTickLookahead = new Settings.Setting<>(150);
   public final Settings.Setting<Integer> pathingMapDefaultSize = new Settings.Setting<>(1024);
   public final Settings.Setting<Float> pathingMapLoadFactor = new Settings.Setting<>(0.75F);
   public final Settings.Setting<Integer> maxFallHeightNoWater = new Settings.Setting<>(3);
   public final Settings.Setting<Integer> maxFallHeightBucket = new Settings.Setting<>(20);
   public final Settings.Setting<Boolean> allowOvershootDiagonalDescend = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> simplifyUnloadedYCoord = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> repackOnAnyBlockChange = new Settings.Setting<>(true);
   public final Settings.Setting<Integer> movementTimeoutTicks = new Settings.Setting<>(100);
   public final Settings.Setting<Long> primaryTimeoutMS = new Settings.Setting<>(500L);
   public final Settings.Setting<Long> failureTimeoutMS = new Settings.Setting<>(2000L);
   public final Settings.Setting<Long> planAheadPrimaryTimeoutMS = new Settings.Setting<>(4000L);
   public final Settings.Setting<Long> planAheadFailureTimeoutMS = new Settings.Setting<>(5000L);
   public final Settings.Setting<Boolean> slowPath = new Settings.Setting<>(false);
   public final Settings.Setting<Long> slowPathTimeDelayMS = new Settings.Setting<>(100L);
   public final Settings.Setting<Long> slowPathTimeoutMS = new Settings.Setting<>(40000L);
   public final Settings.Setting<Boolean> chunkCaching = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> pruneRegionsFromRAM = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> containerMemory = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> backfill = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> chatDebug = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> syncWithOps = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> renderPath = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderPathAsLine = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> renderGoal = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderSelectionBoxes = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderGoalIgnoreDepth = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderGoalXZBeacon = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> renderSelectionBoxesIgnoreDepth = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderPathIgnoreDepth = new Settings.Setting<>(true);
   public final Settings.Setting<Float> pathRenderLineWidthPixels = new Settings.Setting<>(5.0F);
   public final Settings.Setting<Float> goalRenderLineWidthPixels = new Settings.Setting<>(3.0F);
   public final Settings.Setting<Boolean> fadePath = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> freeLook = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> antiCheatCompatibility = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> pathThroughCachedOnly = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> sprintInWater = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> blacklistClosestOnFailure = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderCachedChunks = new Settings.Setting<>(false);
   public final Settings.Setting<Float> cachedChunksOpacity = new Settings.Setting<>(0.5F);
   public final Settings.Setting<Boolean> shortBaritonePrefix = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> echoCommands = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> censorCoordinates = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> censorRanCommands = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> itemSaver = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> preferSilkTouch = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> walkWhileBreaking = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> splicePath = new Settings.Setting<>(true);
   public final Settings.Setting<Integer> maxPathHistoryLength = new Settings.Setting<>(300);
   public final Settings.Setting<Integer> pathHistoryCutoffAmount = new Settings.Setting<>(50);
   public final Settings.Setting<Integer> mineGoalUpdateInterval = new Settings.Setting<>(5);
   public final Settings.Setting<Integer> maxCachedWorldScanCount = new Settings.Setting<>(10);
   public final Settings.Setting<Integer> minYLevelWhileMining = new Settings.Setting<>(-64);
   public final Settings.Setting<Boolean> allowOnlyExposedOres = new Settings.Setting<>(false);
   public final Settings.Setting<Integer> allowOnlyExposedOresDistance = new Settings.Setting<>(1);
   public final Settings.Setting<Boolean> exploreForBlocks = new Settings.Setting<>(true);
   public final Settings.Setting<Integer> worldExploringChunkOffset = new Settings.Setting<>(0);
   public final Settings.Setting<Integer> exploreChunkSetMinimumSize = new Settings.Setting<>(10);
   public final Settings.Setting<Integer> exploreMaintainY = new Settings.Setting<>(64);
   public final Settings.Setting<Boolean> replantCrops = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> replantNetherWart = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> extendCacheOnThreshold = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> buildInLayers = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> layerOrder = new Settings.Setting<>(false);
   public final Settings.Setting<Integer> startAtLayer = new Settings.Setting<>(0);
   public final Settings.Setting<Boolean> skipFailedLayers = new Settings.Setting<>(false);
   public final Settings.Setting<Vec3i> buildRepeat = new Settings.Setting<>(new Vec3i(0, 0, 0));
   public final Settings.Setting<Integer> buildRepeatCount = new Settings.Setting<>(-1);
   public final Settings.Setting<Boolean> buildRepeatSneaky = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> breakFromAbove = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> goalBreakFromAbove = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> mapArtMode = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> okIfWater = new Settings.Setting<>(false);
   public final Settings.Setting<Integer> incorrectSize = new Settings.Setting<>(100);
   public final Settings.Setting<Double> breakCorrectBlockPenaltyMultiplier = new Settings.Setting<>(10.0);
   public final Settings.Setting<Boolean> schematicOrientationX = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> schematicOrientationY = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> schematicOrientationZ = new Settings.Setting<>(false);
   public final Settings.Setting<String> schematicFallbackExtension = new Settings.Setting<>("schematic");
   public final Settings.Setting<Integer> builderTickScanRadius = new Settings.Setting<>(5);
   public final Settings.Setting<Boolean> mineScanDroppedItems = new Settings.Setting<>(true);
   public final Settings.Setting<Long> mineDropLoiterDurationMSThanksLouca = new Settings.Setting<>(250L);
   public final Settings.Setting<Boolean> distanceTrim = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> cancelOnGoalInvalidation = new Settings.Setting<>(true);
   public final Settings.Setting<Integer> axisHeight = new Settings.Setting<>(120);
   public final Settings.Setting<Boolean> disconnectOnArrival = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> legitMine = new Settings.Setting<>(false);
   public final Settings.Setting<Integer> legitMineYLevel = new Settings.Setting<>(11);
   public final Settings.Setting<Boolean> legitMineIncludeDiagonals = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> forceInternalMining = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> internalMiningAirException = new Settings.Setting<>(true);
   public final Settings.Setting<Double> followOffsetDistance = new Settings.Setting<>(0.0);
   public final Settings.Setting<Float> followOffsetDirection = new Settings.Setting<>(0.0F);
   public final Settings.Setting<Integer> followRadius = new Settings.Setting<>(3);
   public final Settings.Setting<Boolean> disableCompletionCheck = new Settings.Setting<>(false);
   public final Settings.Setting<Long> cachedChunksExpirySeconds = new Settings.Setting<>(-1L);
   public final Settings.Setting<Consumer<Component>> logger = new Settings.Setting<>(message -> Minecraft.getInstance().gui.getChat().addMessage(message));
   public final Settings.Setting<Boolean> verboseCommandExceptions = new Settings.Setting<>(false);
   public final Settings.Setting<Double> yLevelBoxSize = new Settings.Setting<>(15.0);
   public final Settings.Setting<Color> colorCurrentPath = new Settings.Setting<>(Color.RED);
   public final Settings.Setting<Color> colorNextPath = new Settings.Setting<>(Color.MAGENTA);
   public final Settings.Setting<Color> colorBlocksToBreak = new Settings.Setting<>(Color.RED);
   public final Settings.Setting<Color> colorBlocksToPlace = new Settings.Setting<>(Color.GREEN);
   public final Settings.Setting<Color> colorBlocksToWalkInto = new Settings.Setting<>(Color.MAGENTA);
   public final Settings.Setting<Color> colorBestPathSoFar = new Settings.Setting<>(Color.BLUE);
   public final Settings.Setting<Color> colorMostRecentConsidered = new Settings.Setting<>(Color.CYAN);
   public final Settings.Setting<Color> colorGoalBox = new Settings.Setting<>(Color.GREEN);
   public final Settings.Setting<Color> colorInvertedGoalBox = new Settings.Setting<>(Color.RED);
   public final Settings.Setting<Color> colorSelection = new Settings.Setting<>(Color.CYAN);
   public final Settings.Setting<Color> colorSelectionPos1 = new Settings.Setting<>(Color.BLACK);
   public final Settings.Setting<Color> colorSelectionPos2 = new Settings.Setting<>(Color.ORANGE);
   public final Settings.Setting<Float> selectionOpacity = new Settings.Setting<>(0.5F);
   public final Settings.Setting<Float> selectionLineWidth = new Settings.Setting<>(2.0F);
   public final Settings.Setting<Boolean> renderSelection = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderSelectionIgnoreDepth = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> renderSelectionCorners = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> useSwordToMine = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> desktopNotifications = new Settings.Setting<>(false);
   public final Settings.Setting<Boolean> notificationOnPathComplete = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> notificationOnFarmFail = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> notificationOnBuildFinished = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> notificationOnExploreFinished = new Settings.Setting<>(true);
   public final Settings.Setting<Boolean> notificationOnMineFail = new Settings.Setting<>(true);
   public final Map<String, Settings.Setting<?>> byLowerName;
   public final List<Settings.Setting<?>> allSettings;
   public final Map<Settings.Setting<?>, Type> settingTypes;

   public Settings() {
      Field[] temp = this.getClass().getFields();
      Map<String, Settings.Setting<?>> tmpByName = new HashMap<>();
      List<Settings.Setting<?>> tmpAll = new ArrayList<>();
      Map<Settings.Setting<?>, Type> tmpSettingTypes = new HashMap<>();

      try {
         for (Field field : temp) {
            if (field.getType().equals(Settings.Setting.class)) {
               Settings.Setting<?> setting = (Settings.Setting<?>)field.get(this);
               String name = field.getName();
               setting.name = name;
               name = name.toLowerCase(Locale.ROOT);
               if (tmpByName.containsKey(name)) {
                  throw new IllegalStateException("Duplicate setting name");
               }

               tmpByName.put(name, setting);
               tmpAll.add(setting);
               tmpSettingTypes.put(setting, ((ParameterizedType)field.getGenericType()).getActualTypeArguments()[0]);
            }
         }
      } catch (IllegalAccessException var11) {
         throw new IllegalStateException(var11);
      }

      this.byLowerName = Collections.unmodifiableMap(tmpByName);
      this.allSettings = Collections.unmodifiableList(tmpAll);
      this.settingTypes = Collections.unmodifiableMap(tmpSettingTypes);
   }

   public <T> List<Settings.Setting<T>> getAllValuesByType(Class<T> cla$$) {
      List<Settings.Setting<T>> result = new ArrayList<>();

      for (Settings.Setting<?> setting : this.allSettings) {
         if (setting.getValueClass().equals(cla$$)) {
            result.add((Settings.Setting<T>)setting);
         }
      }

      return result;
   }

   public final class Setting<T> {
      @Nullable
      private T value;
      public final T defaultValue;
      private String name;

      private Setting(T value) {
         if (value == null) {
            throw new IllegalArgumentException("Cannot determine value type class from null");
         } else {
            this.value = null;
            this.defaultValue = value;
         }
      }

      public T defaultValue() {
         if (Settings.this == BaritoneAPI.getGlobalSettings()) {
            return this.defaultValue;
         } else {
            Settings.Setting<T> globalSetting = (Settings.Setting<T>)BaritoneAPI.getGlobalSettings().byLowerName.get(this.name.toLowerCase(Locale.ROOT));
            return globalSetting.get();
         }
      }

      public final T get() {
         return this.value == null ? this.defaultValue() : this.value;
      }

      public final void set(T value) {
         this.value = value;
      }

      public final String getName() {
         return this.name;
      }

      public Class<T> getValueClass() {
         return (Class<T>)TypeUtils.resolveBaseClass(this.getType());
      }

      @Override
      public String toString() {
         return SettingsUtil.settingToString(this);
      }

      public void reset() {
         this.value = null;
      }

      public final Type getType() {
         return Settings.this.settingTypes.get(this);
      }
   }
}
