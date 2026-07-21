package adris.altoclef.tasks;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.tasks.resources.CollectRecipeCataloguedResourcesTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RecipeTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.entity.IInventoryProvider;
import baritone.api.entity.LivingEntityInventory;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CraftInInventoryTask extends ResourceTask {
   private final RecipeTarget target;
   private final boolean collect;
   private final boolean ignoreUncataloguedSlots;
   private final TimerGame craftTimer = new TimerGame(2.0);
   private boolean isCrafting = false;

   public CraftInInventoryTask(RecipeTarget target, boolean collect, boolean ignoreUncataloguedSlots) {
      super(new ItemTarget(target.getOutputItem(), target.getTargetCount()));
      this.target = target;
      this.collect = collect;
      this.ignoreUncataloguedSlots = ignoreUncataloguedSlots;
      if (target.getRecipe().isBig()) {
         Debug.logError("CraftInInventoryTask was used for a 3x3 recipe. This is not supported. Use CraftInTableTask instead.");
      }
   }

   public CraftInInventoryTask(RecipeTarget target) {
      this(target, true, false);
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController controller) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController controller) {
   }

   @Override
   protected Task onResourceTick(AltoClefController controller) {
      int targetCount = this.target.getTargetCount();
      Item outputItem = this.target.getOutputItem();
      if (controller.getItemStorage().getItemCount(outputItem) >= targetCount) {
         return null;
      } else if (this.collect && !StorageHelper.hasRecipeMaterialsOrTarget(controller, this.target)) {
         this.setDebugState("Collecting ingredients for " + outputItem.getDescription().getString());
         return new CollectRecipeCataloguedResourcesTask(this.ignoreUncataloguedSlots, this.target);
      } else {
         this.setDebugState("Crafting " + outputItem.getDescription().getString());
         if (!this.isCrafting) {
            this.craftTimer.reset();
            this.isCrafting = true;
         }

         if (!this.craftTimer.elapsed()) {
            return null;
         } else {
            int craftsNeeded = (int)Math.ceil(
               (double)(targetCount - controller.getItemStorage().getItemCount(outputItem)) / this.target.getRecipe().outputCount()
            );
            if (craftsNeeded <= 0) {
               return null;
            } else {
               LivingEntityInventory inventory = ((IInventoryProvider)controller.getEntity()).getLivingInventory();

               for (int i = 0; i < craftsNeeded; i++) {
                  if (!StorageHelper.hasRecipeMaterialsOrTarget(
                     controller, new RecipeTarget(this.target.getOutputItem(), this.target.getRecipe().outputCount(), this.target.getRecipe())
                  )) {
                     Debug.logWarning(
                        "Failed to craft " + outputItem.getDescription().getString() + ", not enough ingredients even though we passed the initial check."
                     );
                     break;
                  }

                  for (ItemTarget ingredient : this.target.getRecipe().getSlots()) {
                     if (ingredient != null && !ingredient.isEmpty()) {
                        inventory.remove(stack -> ingredient.matches(stack.getItem()), ingredient.getTargetCount(), inventory);
                     }
                  }

                  ItemStack result = new ItemStack(this.target.getOutputItem(), this.target.getRecipe().outputCount());
                  inventory.insertStack(result);
                  controller.getItemStorage().registerSlotAction();
               }

               controller.getEntity().swing(InteractionHand.MAIN_HAND);
               return null;
            }
         }
      }
   }

   @Override
   protected void onResourceStop(AltoClefController controller, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CraftInInventoryTask task ? task.target.equals(this.target) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Craft in inventory: " + this.target.getOutputItem().getDescription().getString();
   }

   public RecipeTarget getRecipeTarget() {
      return this.target;
   }
}
