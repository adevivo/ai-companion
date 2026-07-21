package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClefController;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.CraftInInventoryTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.CraftingRecipe;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.RecipeTarget;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectGoldNuggetsTask extends ResourceTask {
   private final int count;

   public CollectGoldNuggetsTask(int count) {
      super(Items.GOLD_NUGGET, count);
      this.count = count;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
   }

    protected Task onResourceTick(AltoClefController mod) {
        int potentialNuggies, nuggiesStillNeeded;
        switch (WorldHelper.getCurrentDimension(controller).ordinal()) {
            case 1:
                setDebugState("Getting gold ingots to convert to nuggets");
                potentialNuggies = mod.getItemStorage().getItemCount(new Item[]{Items.GOLD_NUGGET}) + mod.getItemStorage().getItemCount(new Item[]{Items.GOLD_INGOT}) * 9;
                if (potentialNuggies >= this.count && mod.getItemStorage().hasItem(new Item[]{Items.GOLD_INGOT}))
                    return (Task) new CraftInInventoryTask(new RecipeTarget(Items.GOLD_NUGGET, this.count, CraftingRecipe.newShapedRecipe("golden_nuggets", new ItemTarget[]{new ItemTarget(Items.GOLD_INGOT, 1), null, null, null}, 9)));
                nuggiesStillNeeded = this.count - potentialNuggies;
                return (Task) TaskCatalogue.getItemTask(Items.GOLD_INGOT, (int) Math.ceil(nuggiesStillNeeded / 9.0D));
            case 2:
                setDebugState("Mining nuggies");
                return (Task) new MineAndCollectTask(Items.GOLD_NUGGET, this.count, new Block[]{Blocks.NETHER_GOLD_ORE, Blocks.GILDED_BLACKSTONE}, MiningRequirement.WOOD);
            case 3:
                setDebugState("Going to overworld");
                return (Task) new DefaultGoToDimensionTask(Dimension.OVERWORLD);
        }
        setDebugState("INVALID DIMENSION??: " + String.valueOf(WorldHelper.getCurrentDimension(controller)));
        return null;
    }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectGoldNuggetsTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " nuggets";
   }
}
