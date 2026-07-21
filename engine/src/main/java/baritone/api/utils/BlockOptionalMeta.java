package baritone.api.utils;

import baritone.api.utils.accessor.IItemStack;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext.Builder;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public final class BlockOptionalMeta {
   private final Block block;
   private final Set<BlockState> blockstates;
   private final IntSet stateHashes;
   private final IntSet stackHashes;
   private static final Pattern pattern = Pattern.compile("^(.+?)(?::(\\d+))?$");
   private static final Map<Block, List<Item>> drops = new HashMap<>();

   public BlockOptionalMeta(ServerLevel world, @Nonnull Block block) {
      this.block = block;
      this.blockstates = getStates(block);
      this.stateHashes = getStateHashes(this.blockstates);
      this.stackHashes = getStackHashes(world, this.blockstates);
   }

   public BlockOptionalMeta(ServerLevel world, @Nonnull String selector) {
      Matcher matcher = pattern.matcher(selector);
      if (!matcher.find()) {
         throw new IllegalArgumentException("invalid block selector");
      } else {
         MatchResult matchResult = matcher.toMatchResult();
         this.block = BlockUtils.stringToBlockRequired(matchResult.group(1));
         this.blockstates = getStates(this.block);
         this.stateHashes = getStateHashes(this.blockstates);
         this.stackHashes = getStackHashes(world, this.blockstates);
      }
   }

   private static Set<BlockState> getStates(@Nonnull Block block) {
      return new HashSet<>(block.getStateDefinition().getPossibleStates());
   }

   private static IntSet getStateHashes(Set<BlockState> blockstates) {
      return blockstates.stream().map(Object::hashCode).collect(Collectors.toCollection(IntOpenHashSet::new));
   }

   private static IntSet getStackHashes(ServerLevel world, Set<BlockState> blockstates) {
      return blockstates.stream()
         .flatMap(state -> drops(world, state.getBlock()).stream().map(item -> new ItemStack(item, 1)))
         .map(stack -> ((IItemStack)(Object)stack).getBaritoneHash())
         .collect(Collectors.toCollection(IntOpenHashSet::new));
   }

   public Block getBlock() {
      return this.block;
   }

   public boolean matches(@Nonnull Block block) {
      return block == this.block;
   }

   public boolean matches(@Nonnull BlockState blockstate) {
      Block block = blockstate.getBlock();
      return block == this.block && this.stateHashes.contains(blockstate.hashCode());
   }

   public boolean matches(ItemStack stack) {
      int hash = ((IItemStack)(Object)stack).getBaritoneHash();
      hash -= stack.getDamageValue();
      return this.stackHashes.contains(hash);
   }

   @Override
   public String toString() {
      return String.format("BlockOptionalMeta{block=%s}", this.block);
   }

   public BlockState getAnyBlockState() {
      return this.blockstates.size() > 0 ? this.blockstates.iterator().next() : null;
   }

   private static synchronized List<Item> drops(ServerLevel world, Block b) {
      return drops.computeIfAbsent(
         b,
         block -> {
            ResourceLocation lootTableLocation = block.getLootTable();
            if (lootTableLocation == BuiltInLootTables.EMPTY) {
               return Collections.emptyList();
            } else {
               List<Item> items = new ArrayList<>();
               world.getServer()
                  .getLootData()
                  .getLootTable(lootTableLocation)
                  .getRandomItems(
                     new Builder(
                           new net.minecraft.world.level.storage.loot.LootParams.Builder(world)
                              .withParameter(LootContextParams.ORIGIN, Vec3.atLowerCornerOf(BlockPos.ZERO))
                              .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                              .withOptionalParameter(LootContextParams.BLOCK_ENTITY, null)
                              .withParameter(LootContextParams.BLOCK_STATE, block.defaultBlockState())
                              .create(LootContextParamSets.BLOCK)
                        )
                        .withOptionalRandomSeed(world.getSeed())
                        .create(null),
                     stack -> items.add(stack.getItem())
                  );
               return items;
            }
         }
      );
   }
}
