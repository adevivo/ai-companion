package adris.altoclef.tasks.construction.build_structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import adris.altoclef.AltoClefController;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.build_structure.StructureFromCode.SetBlockCommand;
import adris.altoclef.util.ItemTarget;
import baritone.api.entity.LivingEntityInventory;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Survival cost model for {@link BuildStructureTask}.
 *
 * <p>The build DSL writes blocks straight into the world, so nothing about it is bound by normal
 * placement rules. This class puts a price on each block so the companion has to actually own the
 * materials first. Costs are charged against its own inventory, no containers.
 *
 * <p>Three kinds of block:
 * <ul>
 * <li><b>Consumed</b> — one item per block, the ordinary case.
 * <li><b>Required</b> — liquids. A player refills a bucket from a source they just placed, so one
 * water bucket is enough for a whole irrigation row. The companion must own the bucket, but it is
 * never used up.
 * <li><b>Free</b> — air, and any block with no obtainable item form.
 * </ul>
 */
public final class BuildMaterials {

    private BuildMaterials() {
    }

    /**
     * Blocks whose item form is {@code AIR} but which a player still places from a real item.
     * {@link Block#asItem()} would report these as free, so they need spelling out.
     */
    private static final Map<Block, Item> PLACED_FROM = Map.of(
            Blocks.FARMLAND, Items.DIRT,
            Blocks.DIRT_PATH, Items.DIRT,
            Blocks.WHEAT, Items.WHEAT_SEEDS,
            Blocks.CARROTS, Items.CARROT,
            Blocks.POTATOES, Items.POTATO,
            Blocks.BEETROOTS, Items.BEETROOT_SEEDS);

    /** Liquids and the bucket you need in hand to place them. Checked, never consumed. */
    private static final Map<Block, Item> LIQUID_SOURCE = Map.of(
            Blocks.WATER, Items.WATER_BUCKET,
            Blocks.LAVA, Items.LAVA_BUCKET);

    /** What a structure costs: items used up, plus items that merely have to be carried. */
    public record Bill(Map<Item, Integer> consumed, Set<Item> required) {
        public boolean isFree() {
            return consumed.isEmpty() && required.isEmpty();
        }
    }

    /**
     * Resolves a DSL block name. Unknown or malformed names resolve to air, which the DSL's own
     * validation pass already tolerates.
     */
    public static Block resolveBlock(String blockName) {
        String raw = blockName == null ? "" : blockName.trim();
        ResourceLocation id = ResourceLocation.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
        return id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id);
    }

    /** The item consumed to place one of {@code block}, or null if the block is free or a liquid. */
    public static Item consumedItemFor(Block block) {
        if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
            return null;
        }
        if (LIQUID_SOURCE.containsKey(block)) {
            return null;
        }
        Item mapped = PLACED_FROM.get(block);
        if (mapped != null) {
            return mapped;
        }
        // No item form and no mapping — a technical block the companion could never have carried.
        // Charging for it would deadlock the build, so let it through free.
        Item item = block.asItem();
        return item == Items.AIR ? null : item;
    }

    /** Totals up what the whole structure will cost before a single block is placed. */
    public static Bill tally(List<SetBlockCommand> commands) {
        Map<Item, Integer> consumed = new LinkedHashMap<>();
        Set<Item> required = new LinkedHashSet<>();
        for (SetBlockCommand command : commands) {
            Block block = resolveBlock(command.blockName);
            Item bucket = LIQUID_SOURCE.get(block);
            if (bucket != null) {
                required.add(bucket);
                continue;
            }
            Item item = consumedItemFor(block);
            if (item != null) {
                consumed.merge(item, 1, Integer::sum);
            }
        }
        return new Bill(consumed, required);
    }

    /**
     * What the bill asks for beyond what the companion is carrying, as item to number still needed.
     * Empty means the build can go ahead.
     */
    public static Map<Item, Integer> shortfall(AltoClefController mod, Bill bill) {
        LivingEntityInventory inventory = mod.getInventory();
        Map<Item, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> entry : bill.consumed().entrySet()) {
            int held = count(inventory, entry.getKey());
            if (held < entry.getValue()) {
                missing.put(entry.getKey(), entry.getValue() - held);
            }
        }
        for (Item bucket : bill.required()) {
            if (count(inventory, bucket) < 1) {
                missing.put(bucket, 1);
            }
        }
        return missing;
    }

    /**
     * Takes {@code count} of {@code item} out of the companion's inventory. Returns false if it ran
     * out partway, which the pre-flight shortfall check should have already ruled out.
     */
    public static boolean consume(AltoClefController mod, Item item, int count) {
        LivingEntityInventory inventory = mod.getInventory();
        int remaining = count;
        remaining = takeFrom(inventory.main, item, remaining);
        remaining = takeFrom(inventory.offHand, item, remaining);
        inventory.setChanged();
        return remaining == 0;
    }

    /**
     * The whole bill as gather targets, for handing to {@code TaskCatalogue}.
     *
     * <p>Quantities are the <b>full billed amount</b>, not the shortfall. {@link ItemTarget} counts
     * are totals — "end up holding this many" — which is why {@code GetCommand} runs
     * {@code AgentCommandUtils.addPresentItemsToTargets} to add what is already carried before
     * passing them on. Targeting the deficit instead would under-gather every partial holding: need
     * 3, hold 2, deficit 1, target 1 — already satisfied, so nothing is collected and the build fails
     * again for the same reason. Using the billed total also makes this a no-op for anything already
     * in the inventory, so it only fetches what is genuinely missing.
     *
     * <p>Items with no resource task are dropped: {@code TaskCatalogue.getItemTask} returns null for
     * those, and a null in the list would take the whole gather down.
     */
    public static List<ItemTarget> gatherTargets(Bill bill) {
        List<ItemTarget> targets = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : bill.consumed().entrySet()) {
            if (TaskCatalogue.taskExists(entry.getKey())) {
                targets.add(new ItemTarget(entry.getKey(), entry.getValue()));
            }
        }
        for (Item bucket : bill.required()) {
            if (TaskCatalogue.taskExists(bucket)) {
                targets.add(new ItemTarget(bucket, 1));
            }
        }
        return targets;
    }

    /** Registry path of an item, e.g. {@code wheat_seeds} — the spelling the `get` command wants. */
    public static String name(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? item.toString() : key.getPath();
    }

    /** Renders a bill or shortfall as "64 dirt, 40 wheat_seeds". For the log and the agent. */
    public static String describe(Map<Item, Integer> items) {
        return items.entrySet().stream()
                .map(entry -> entry.getValue() + " " + name(entry.getKey()))
                .collect(Collectors.joining(", "));
    }

    /**
     * The same list written for a person: "64 dirt, 40 wheat seeds and 1 chest".
     *
     * <p>Registry ids are what the agent needs in order to `get` things, but they read badly in chat,
     * which is where the owner finds out a build could not afford itself.
     */
    public static String describeForPlayer(Map<Item, Integer> items) {
        List<String> parts = items.entrySet().stream()
                .map(entry -> entry.getValue() + " " + name(entry.getKey()).replace('_', ' '))
                .collect(Collectors.toList());
        if (parts.size() <= 1) {
            return String.join("", parts);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    /**
     * Counted here rather than through ItemStorageTracker because a build consumes items across
     * several ticks and the tracker only refreshes once per tick.
     */
    private static int count(LivingEntityInventory inventory, Item item) {
        return countIn(inventory.main, item) + countIn(inventory.offHand, item);
    }

    private static int countIn(NonNullList<ItemStack> slots, Item item) {
        int total = 0;
        for (ItemStack stack : slots) {
            if (!stack.isEmpty() && stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int takeFrom(NonNullList<ItemStack> slots, Item item, int remaining) {
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty() || stack.getItem() != item) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            if (stack.isEmpty()) {
                slots.set(i, ItemStack.EMPTY);
            }
            remaining -= taken;
        }
        return remaining;
    }
}
