package adris.altoclef;

import baritone.api.utils.RayTraceUtils;
import baritone.autoclef.AltoClefSettings;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;

public class BotBehaviour {
   private final AltoClefController mod;
   Deque<BotBehaviour.State> states = new ArrayDeque<>();

   public BotBehaviour(AltoClefController mod) {
      this.mod = mod;
      this.push();
   }

   public boolean shouldEscapeLava() {
      return this.current().escapeLava;
   }

   public void setEscapeLava(boolean allow) {
      this.current().escapeLava = allow;
      this.current().applyState();
   }

   public void setFollowDistance(double distance) {
      this.current().followOffsetDistance = distance;
      this.current().applyState();
   }

   public void setMineScanDroppedItems(boolean value) {
      this.current().mineScanDroppedItems = value;
      this.current().applyState();
   }

   public boolean exclusivelyMineLogs() {
      return this.current().exclusivelyMineLogs;
   }

   public void setExclusivelyMineLogs(boolean value) {
      this.current().exclusivelyMineLogs = value;
      this.current().applyState();
   }

   public boolean shouldExcludeFromForcefield(Entity entity) {
      if (!this.current().excludeFromForceField.isEmpty()) {
         for (Predicate<Entity> pred : this.current().excludeFromForceField) {
            if (pred.test(entity)) {
               return true;
            }
         }
      }

      return false;
   }

   public void addForceFieldExclusion(Predicate<Entity> pred) {
      this.current().excludeFromForceField.add(pred);
   }

   public void avoidBlockBreaking(BlockPos pos) {
      this.current().blocksToAvoidBreaking.add(pos);
      this.current().applyState();
   }

   public void avoidBlockBreaking(Predicate<BlockPos> pred) {
      this.current().toAvoidBreaking.add(pred);
      this.current().applyState();
   }

   public void avoidBlockPlacing(Predicate<BlockPos> pred) {
      this.current().toAvoidPlacing.add(pred);
      this.current().applyState();
   }

   public void allowWalkingOn(Predicate<BlockPos> pred) {
      this.current().allowWalking.add(pred);
      this.current().applyState();
   }

   public void avoidWalkingThrough(Predicate<BlockPos> pred) {
      this.current().avoidWalkingThrough.add(pred);
      this.current().applyState();
   }

   public void forceUseTool(BiPredicate<BlockState, ItemStack> pred) {
      this.current().forceUseTools.add(pred);
      this.current().applyState();
   }

   public void setRayTracingFluidHandling(Fluid fluidHandling) {
      this.current().rayFluidHandling = fluidHandling;
      this.current().applyState();
   }

   public void setAllowWalkThroughFlowingWater(boolean value) {
      this.current().allowWalkThroughFlowingWater = value;
      this.current().applyState();
   }

   public void setPauseOnLostFocus(boolean pauseOnLostFocus) {
      this.current().pauseOnLostFocus = pauseOnLostFocus;
      this.current().applyState();
   }

   public void addProtectedItems(Item... items) {
      Collections.addAll(this.current().protectedItems, items);
      this.current().applyState();
   }

   public void removeProtectedItems(Item... items) {
      this.current().protectedItems.removeAll(Arrays.asList(items));
      this.current().applyState();
   }

   public boolean isProtected(Item item) {
      return this.current().protectedItems.contains(item);
   }

   public boolean shouldForceFieldPlayers() {
      return this.current().forceFieldPlayers;
   }

   public void setForceFieldPlayers(boolean forceFieldPlayers) {
      this.current().forceFieldPlayers = forceFieldPlayers;
   }

   public void allowSwimThroughLava(boolean allow) {
      this.current().swimThroughLava = allow;
      this.current().applyState();
   }

   public void setPreferredStairs(boolean allow) {
      this.current().applyState();
   }

   public void setAllowDiagonalAscend(boolean allow) {
      this.current().allowDiagonalAscend = allow;
      this.current().applyState();
   }

   public void setBlockPlacePenalty(double penalty) {
      this.current().blockPlacePenalty = penalty;
      this.current().applyState();
   }

   public void setBlockBreakAdditionalPenalty(double penalty) {
      this.current().blockBreakAdditionalPenalty = penalty;
      this.current().applyState();
   }

   public void avoidDodgingProjectile(Predicate<Entity> whenToDodge) {
      this.current().avoidDodgingProjectile.add(whenToDodge);
   }

   public void addGlobalHeuristic(BiFunction<Double, BlockPos, Double> heuristic) {
      this.current().globalHeuristics.add(heuristic);
      this.current().applyState();
   }

   public boolean shouldAvoidDodgingProjectile(Entity entity) {
      if (!this.current().avoidDodgingProjectile.isEmpty()) {
         for (Predicate<Entity> test : this.current().avoidDodgingProjectile) {
            if (test.test(entity)) {
               return true;
            }
         }
      }

      return false;
   }

   public void push() {
      if (this.states.isEmpty()) {
         this.states.push(new BotBehaviour.State());
      } else {
         this.states.push(new BotBehaviour.State(this.current()));
      }
   }

   public void push(BotBehaviour.State customState) {
      this.states.push(customState);
   }

   public BotBehaviour.State pop() {
      if (this.states.isEmpty()) {
         baritone.utils.Debug.logError("State stack is empty. This shouldn't be happening.");
         return null;
      } else {
         BotBehaviour.State popped = this.states.pop();
         if (this.states.isEmpty()) {
            baritone.utils.Debug.logError("State stack is empty after pop. This shouldn't be happening.");
            return null;
         } else {
            this.states.peek().applyState();
            return popped;
         }
      }
   }

   private BotBehaviour.State current() {
      if (this.states.isEmpty()) {
         baritone.utils.Debug.logError("STATE EMPTY, UNEMPTIED!");
         this.push();
      }

      return this.states.peek();
   }

   private class State {
      public double followOffsetDistance;
      public HashSet<Item> protectedItems = new HashSet<>();
      public boolean mineScanDroppedItems;
      public boolean swimThroughLava;
      public boolean allowDiagonalAscend;
      public double blockPlacePenalty;
      public double blockBreakAdditionalPenalty;
      public boolean exclusivelyMineLogs;
      public boolean forceFieldPlayers;
      public List<Predicate<Entity>> avoidDodgingProjectile = new ArrayList<>();
      public List<Predicate<Entity>> excludeFromForceField = new ArrayList<>();
      public HashSet<BlockPos> blocksToAvoidBreaking = new HashSet<>();
      public List<Predicate<BlockPos>> toAvoidBreaking = new ArrayList<>();
      public List<Predicate<BlockPos>> toAvoidPlacing = new ArrayList<>();
      public List<Predicate<BlockPos>> allowWalking = new ArrayList<>();
      public List<Predicate<BlockPos>> avoidWalkingThrough = new ArrayList<>();
      public List<BiPredicate<BlockState, ItemStack>> forceUseTools = new ArrayList<>();
      public List<BiFunction<Double, BlockPos, Double>> globalHeuristics = new ArrayList<>();
      public boolean allowWalkThroughFlowingWater = false;
      public boolean pauseOnLostFocus = true;
      public Fluid rayFluidHandling;
      public boolean escapeLava = true;

      public State() {
         this(null);
      }

      public State(BotBehaviour.State toCopy) {
         this.readState(BotBehaviour.this.mod.getBaritoneSettings());
         this.readExtraState(BotBehaviour.this.mod.getExtraBaritoneSettings());
         this.readMinecraftState();
         if (toCopy != null) {
            this.exclusivelyMineLogs = toCopy.exclusivelyMineLogs;
            this.avoidDodgingProjectile.addAll(toCopy.avoidDodgingProjectile);
            this.excludeFromForceField.addAll(toCopy.excludeFromForceField);
            this.forceFieldPlayers = toCopy.forceFieldPlayers;
            this.escapeLava = toCopy.escapeLava;
         }
      }

      public void applyState() {
         this.applyState(BotBehaviour.this.mod.getBaritoneSettings(), BotBehaviour.this.mod.getExtraBaritoneSettings());
      }

      private void readState(baritone.api.Settings s) {
         this.followOffsetDistance = s.followOffsetDistance.get();
         this.mineScanDroppedItems = s.mineScanDroppedItems.get();
         this.swimThroughLava = s.assumeWalkOnLava.get();
         this.allowDiagonalAscend = s.allowDiagonalAscend.get();
         this.blockPlacePenalty = s.blockPlacementPenalty.get();
         this.blockBreakAdditionalPenalty = s.blockBreakAdditionalPenalty.get();
      }

      private void readExtraState(AltoClefSettings settings) {
         synchronized (settings.getBreakMutex()) {
            synchronized (settings.getPlaceMutex()) {
               this.blocksToAvoidBreaking = new HashSet<>(settings.getBlocksToAvoidBreaking());
               this.toAvoidBreaking = new ArrayList<>(settings.getBreakAvoiders());
               this.toAvoidPlacing = new ArrayList<>(settings.getPlaceAvoiders());
               this.protectedItems = new HashSet<>(settings.getProtectedItems());
               synchronized (settings.getPropertiesMutex()) {
                  this.allowWalking = new ArrayList<>(settings.getForceWalkOnPredicates());
                  this.avoidWalkingThrough = new ArrayList<>(settings.getForceAvoidWalkThroughPredicates());
                  this.forceUseTools = new ArrayList<>(settings.getForceUseToolPredicates());
               }
            }
         }

         synchronized (settings.getGlobalHeuristicMutex()) {
            this.globalHeuristics = new ArrayList<>(settings.getGlobalHeuristics());
         }

         this.allowWalkThroughFlowingWater = settings.isFlowingWaterPassAllowed();
         this.rayFluidHandling = RayTraceUtils.fluidHandling;
      }

      private void readMinecraftState() {
         this.pauseOnLostFocus = false;
      }

      private void applyState(baritone.api.Settings s, AltoClefSettings sa) {
         s.followOffsetDistance.set(this.followOffsetDistance);
         s.mineScanDroppedItems.set(this.mineScanDroppedItems);
         s.allowDiagonalAscend.set(this.allowDiagonalAscend);
         s.blockPlacementPenalty.set(this.blockPlacePenalty);
         s.blockBreakAdditionalPenalty.set(this.blockBreakAdditionalPenalty);
         synchronized (sa.getBreakMutex()) {
            synchronized (sa.getPlaceMutex()) {
               sa.getBreakAvoiders().clear();
               sa.getBreakAvoiders().addAll(this.toAvoidBreaking);
               sa.getBlocksToAvoidBreaking().clear();
               sa.getBlocksToAvoidBreaking().addAll(this.blocksToAvoidBreaking);
               sa.getPlaceAvoiders().clear();
               sa.getPlaceAvoiders().addAll(this.toAvoidPlacing);
               sa.getProtectedItems().clear();
               sa.getProtectedItems().addAll(this.protectedItems);
               synchronized (sa.getPropertiesMutex()) {
                  sa.getForceWalkOnPredicates().clear();
                  sa.getForceWalkOnPredicates().addAll(this.allowWalking);
                  sa.getForceAvoidWalkThroughPredicates().clear();
                  sa.getForceAvoidWalkThroughPredicates().addAll(this.avoidWalkingThrough);
                  sa.getForceUseToolPredicates().clear();
                  sa.getForceUseToolPredicates().addAll(this.forceUseTools);
               }
            }
         }

         synchronized (sa.getGlobalHeuristicMutex()) {
            sa.getGlobalHeuristics().clear();
            sa.getGlobalHeuristics().addAll(this.globalHeuristics);
         }

         sa.setFlowingWaterPass(this.allowWalkThroughFlowingWater);
         sa.allowSwimThroughLava(this.swimThroughLava);
         RayTraceUtils.fluidHandling = this.rayFluidHandling;
      }
   }
}
