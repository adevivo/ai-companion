package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.util.helpers.ItemHelper;
import baritone.api.entity.LivingEntityHungerManager;
import baritone.api.entity.LivingEntityInventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Eat one item of food out of the companion's own inventory, right now.
 *
 * <p>Distinct from {@code food}, which forages for more. Asked to eat, a model would reach for
 * {@code food} — the only food-shaped command that existed — and set off on a gathering expedition
 * instead; in one session that happened at 3/20 health with four cooked meats already in the pack.
 *
 * <p>Consumption is done directly against the inventory rather than by simulating a right-click.
 * {@code LivingEntityInteractionManager.interactItem} passes a null {@code Player} to
 * {@code ItemStack.use}, and vanilla's {@code Item.use} dereferences that argument on its first
 * instruction for anything edible, so the whole path throws instantly and the exception is swallowed.
 */
public class EatCommand extends Command {

    public EatCommand() throws CommandException {
        super(
            "eat",
            "Eat one item of food from your inventory immediately, to restore hunger. The item is"
                + " optional: bare `eat` picks the best food you are carrying, or name one, e.g."
                + " `eat cooked_mutton`. This does NOT gather food — use `food` for that.",
            // Optional, same reasoning as FarmCommand: a bare `eat` is the natural thing to emit, and
            // hard-failing on the missing argument burns a whole LLM round-trip recovering.
            new Arg<>(String.class, "item", null, 0)
        );
    }

    @Override
    protected void call(AltoClefController mod, ArgParser parser) throws CommandException {
        String requested = parser.get(String.class);
        LivingEntityHungerManager hunger = mod.getBaritone().getEntityContext().hungerManager();

        // Refuse rather than waste it: eating at a full bar destroys the item for nothing, exactly as
        // it does for a player. Hunger does now deplete — healing spends it, see
        // LivingEntityHungerManager.tickCompanion — so a full bar means genuinely well fed rather than
        // the permanent 20/20 this guard used to be papering over.
        if (hunger.getFoodLevel() >= 20) {
            mod.logAgentNotice("Did not eat: already at full food (" + hunger.getFoodLevel()
                    + "/20), so eating would waste the item. Health recovers on its own while well fed.");
            this.finish();
            return;
        }

        ItemStack chosen = findFood(mod, requested);
        if (chosen == null) {
            mod.logAgentNotice(requested == null
                    ? "Did not eat: no edible food in inventory. Use `food <n>` to go and collect some."
                    : "Did not eat: no edible '" + requested.strip()
                            + "' in inventory. Use `food <n>` to collect food, or name something you are carrying.");
            this.finish();
            return;
        }

        Item item = chosen.getItem();
        hunger.eat(item, chosen);
        chosen.shrink(1);
        mod.getInventory().setChanged();
        mod.log("Ate " + ItemHelper.stripItemName(item) + " (food now " + hunger.getFoodLevel() + "/20).");
        this.finish();
    }

    /**
     * The stack to eat: the named item if one was given, otherwise the most filling food carried.
     * Returns null when nothing matches.
     */
    private static ItemStack findFood(AltoClefController mod, String requested) {
        String wanted = requested == null ? null : requested.strip().toLowerCase();
        LivingEntityInventory inventory = mod.getInventory();
        ItemStack best = null;
        int bestNutrition = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !stack.getItem().isEdible()) {
                continue;
            }
            String name = ItemHelper.stripItemName(stack.getItem());
            if (wanted != null) {
                if (name.equalsIgnoreCase(wanted)) {
                    return stack;
                }
                continue;
            }
            FoodProperties food = stack.getItem().getFoodProperties();
            int nutrition = food == null ? 0 : food.getNutrition();
            if (nutrition > bestNutrition) {
                bestNutrition = nutrition;
                best = stack;
            }
        }
        return best;
    }
}
