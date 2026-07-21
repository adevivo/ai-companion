package baritone.autoclef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class AltoClefSettings {
   private final Object breakMutex = new Object();
   private final Object placeMutex = new Object();
   private final Object propertiesMutex = new Object();
   private final Object globalHeuristicMutex = new Object();
   private final HashSet<BlockPos> blocksToAvoidBreaking = new HashSet<>();
   private final List<Predicate<BlockPos>> breakAvoiders = new ArrayList<>();
   private final List<Predicate<BlockPos>> placeAvoiders = new ArrayList<>();
   private final List<Predicate<BlockPos>> forceCanWalkOn = new ArrayList<>();
   private final List<Predicate<BlockPos>> forceAvoidWalkThrough = new ArrayList<>();
   private final List<BiPredicate<BlockState, ItemStack>> forceSaveTool = new ArrayList<>();
   private final List<BiPredicate<BlockState, ItemStack>> forceUseTool = new ArrayList<>();
   private final List<BiFunction<Double, BlockPos, Double>> globalHeuristics = new ArrayList<>();
   private final HashSet<Item> protectedItems = new HashSet<>();
   private boolean allowFlowingWaterPass;
   private boolean pauseInteractions;
   private boolean dontPlaceBucketButStillFall;
   private boolean allowSwimThroughLava = false;
   private boolean treatSoulSandAsOrdinaryBlock = false;
   private boolean canWalkOnEndPortal = false;

   public void canWalkOnEndPortal(boolean canWalk) {
      this.canWalkOnEndPortal = canWalk;
   }

   public void avoidBlockBreak(BlockPos pos) {
      synchronized (this.breakMutex) {
         this.blocksToAvoidBreaking.add(pos);
      }
   }

   public void avoidBlockBreak(Predicate<BlockPos> avoider) {
      synchronized (this.breakMutex) {
         this.breakAvoiders.add(avoider);
      }
   }

   public void configurePlaceBucketButDontFall(boolean allow) {
      synchronized (this.propertiesMutex) {
         this.dontPlaceBucketButStillFall = allow;
      }
   }

   public void treatSoulSandAsOrdinaryBlock(boolean enable) {
      synchronized (this.propertiesMutex) {
         this.treatSoulSandAsOrdinaryBlock = enable;
      }
   }

   public void avoidBlockPlace(Predicate<BlockPos> avoider) {
      synchronized (this.placeMutex) {
         this.placeAvoiders.add(avoider);
      }
   }

   public boolean shouldForceSaveTool(BlockState state, ItemStack tool) {
      synchronized (this.propertiesMutex) {
         return this.forceSaveTool.stream().anyMatch(pred -> pred.test(state, tool));
      }
   }

   public boolean shouldAvoidBreaking(int x, int y, int z) {
      return this.shouldAvoidBreaking(new BlockPos(x, y, z));
   }

   public boolean shouldAvoidBreaking(BlockPos pos) {
      synchronized (this.breakMutex) {
         return this.blocksToAvoidBreaking.contains(pos) ? true : this.breakAvoiders.stream().anyMatch(pred -> pred.test(pos));
      }
   }

   public boolean shouldAvoidPlacingAt(BlockPos pos) {
      synchronized (this.placeMutex) {
         return this.placeAvoiders.stream().anyMatch(pred -> pred.test(pos));
      }
   }

   public boolean shouldAvoidPlacingAt(int x, int y, int z) {
      return this.shouldAvoidPlacingAt(new BlockPos(x, y, z));
   }

   public boolean canWalkOnForce(int x, int y, int z) {
      synchronized (this.propertiesMutex) {
         return this.forceCanWalkOn.stream().anyMatch(pred -> pred.test(new BlockPos(x, y, z)));
      }
   }

   public boolean shouldAvoidWalkThroughForce(BlockPos pos) {
      synchronized (this.propertiesMutex) {
         return this.forceAvoidWalkThrough.stream().anyMatch(pred -> pred.test(pos));
      }
   }

   public boolean shouldAvoidWalkThroughForce(int x, int y, int z) {
      return this.shouldAvoidWalkThroughForce(new BlockPos(x, y, z));
   }

   public boolean shouldForceUseTool(BlockState state, ItemStack tool) {
      synchronized (this.propertiesMutex) {
         return this.forceUseTool.stream().anyMatch(pred -> pred.test(state, tool));
      }
   }

   public boolean shouldNotPlaceBucketButStillFall() {
      synchronized (this.propertiesMutex) {
         return this.dontPlaceBucketButStillFall;
      }
   }

   public boolean shouldTreatSoulSandAsOrdinaryBlock() {
      synchronized (this.propertiesMutex) {
         return this.treatSoulSandAsOrdinaryBlock;
      }
   }

   public boolean isInteractionPaused() {
      synchronized (this.propertiesMutex) {
         return this.pauseInteractions;
      }
   }

   public void setInteractionPaused(boolean paused) {
      synchronized (this.propertiesMutex) {
         this.pauseInteractions = paused;
      }
   }

   public boolean isFlowingWaterPassAllowed() {
      synchronized (this.propertiesMutex) {
         return this.allowFlowingWaterPass;
      }
   }

   public boolean canSwimThroughLava() {
      synchronized (this.propertiesMutex) {
         return this.allowSwimThroughLava;
      }
   }

   public void setFlowingWaterPass(boolean pass) {
      synchronized (this.propertiesMutex) {
         this.allowFlowingWaterPass = pass;
      }
   }

   public void allowSwimThroughLava(boolean allow) {
      synchronized (this.propertiesMutex) {
         this.allowSwimThroughLava = allow;
      }
   }

   public double applyGlobalHeuristic(double prev, int x, int y, int z) {
      return prev;
   }

   public HashSet<BlockPos> getBlocksToAvoidBreaking() {
      return this.blocksToAvoidBreaking;
   }

   public List<Predicate<BlockPos>> getBreakAvoiders() {
      return this.breakAvoiders;
   }

   public List<Predicate<BlockPos>> getPlaceAvoiders() {
      return this.placeAvoiders;
   }

   public List<Predicate<BlockPos>> getForceWalkOnPredicates() {
      return this.forceCanWalkOn;
   }

   public List<Predicate<BlockPos>> getForceAvoidWalkThroughPredicates() {
      return this.forceAvoidWalkThrough;
   }

   public List<BiPredicate<BlockState, ItemStack>> getForceSaveToolPredicates() {
      return this.forceSaveTool;
   }

   public List<BiPredicate<BlockState, ItemStack>> getForceUseToolPredicates() {
      return this.forceUseTool;
   }

   public List<BiFunction<Double, BlockPos, Double>> getGlobalHeuristics() {
      return this.globalHeuristics;
   }

   public boolean isItemProtected(Item item) {
      return this.protectedItems.contains(item);
   }

   public HashSet<Item> getProtectedItems() {
      return this.protectedItems;
   }

   public void protectItem(Item item) {
      this.protectedItems.add(item);
   }

   public void stopProtectingItem(Item item) {
      this.protectedItems.remove(item);
   }

   public Object getBreakMutex() {
      return this.breakMutex;
   }

   public Object getPlaceMutex() {
      return this.placeMutex;
   }

   public Object getPropertiesMutex() {
      return this.propertiesMutex;
   }

   public Object getGlobalHeuristicMutex() {
      return this.globalHeuristicMutex;
   }

   public boolean isCanWalkOnEndPortal() {
      return this.canWalkOnEndPortal;
   }
}
