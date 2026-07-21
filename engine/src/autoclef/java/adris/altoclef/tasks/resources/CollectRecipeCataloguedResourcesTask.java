package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.CraftingRecipe;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RecipeTarget;
import adris.altoclef.util.helpers.StorageHelper;
import java.util.Arrays;
import java.util.HashMap;
import net.minecraft.world.item.Item;
import org.apache.commons.lang3.ArrayUtils;

public class CollectRecipeCataloguedResourcesTask extends Task {
   private final RecipeTarget[] targets;
   private final boolean ignoreUncataloguedSlots;
   private boolean finished = false;

   public CollectRecipeCataloguedResourcesTask(boolean ignoreUncataloguedSlots, RecipeTarget... targets) {
      this.targets = targets;
      this.ignoreUncataloguedSlots = ignoreUncataloguedSlots;
   }

   @Override
   protected void onStart() {
      this.finished = false;
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      HashMap<String, Integer> catalogueCount = new HashMap<>();
      HashMap<Item, Integer> itemCount = new HashMap<>();

      for (RecipeTarget target : this.targets) {
         if (target != null) {
            int weNeed = target.getTargetCount() - mod.getItemStorage().getItemCount(target.getOutputItem());
            if (weNeed > 0) {
               CraftingRecipe recipe = target.getRecipe();

               for (int i = 0; i < recipe.getSlotCount(); i++) {
                  ItemTarget slot = recipe.getSlot(i);
                  if (slot != null && !slot.isEmpty()) {
                     int numberOfRepeats = (int)Math.floor(-0.1 + (double)weNeed / target.getRecipe().outputCount()) + 1;
                     if (!slot.isCatalogueItem()) {
                        if (slot.getMatches().length != 1) {
                           if (!this.ignoreUncataloguedSlots) {
                              Debug.logWarning(
                                 "Recipe collection for recipe "
                                    + recipe
                                    + " slot "
                                    + i
                                    + " is not catalogued. Please define an explicit collectRecipeSubTask() function for this item target:"
                                    + slot
                              );
                           }
                        } else {
                           Item item = slot.getMatches()[0];
                           itemCount.put(item, itemCount.getOrDefault(item, 0) + numberOfRepeats);
                        }
                     } else {
                        String targetName = slot.getCatalogueName();
                        catalogueCount.put(targetName, catalogueCount.getOrDefault(targetName, 0) + numberOfRepeats);
                     }
                  }
               }
            }
         }
      }

      for (String catalogueMaterialName : catalogueCount.keySet()) {
         int count = catalogueCount.get(catalogueMaterialName);
         ItemTarget itemTarget = new ItemTarget(catalogueMaterialName, count);
         if (count > 0 && !StorageHelper.itemTargetsMet(mod, itemTarget)) {
            this.setDebugState("Getting " + itemTarget);
            return TaskCatalogue.getItemTask(catalogueMaterialName, count);
         }
      }

      for (Item item : itemCount.keySet()) {
         int count = itemCount.get(item);
         if (count > 0 && mod.getItemStorage().getItemCount(item) < count) {
            this.setDebugState("Getting " + item.getDescriptionId());
            return TaskCatalogue.getItemTask(item, count);
         }
      }

      this.finished = true;
      return null;
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof CollectRecipeCataloguedResourcesTask task ? Arrays.equals((Object[])task.targets, (Object[])this.targets) : false;
   }

   @Override
   protected String toDebugString() {
      return "Collect Recipe Resources: " + ArrayUtils.toString(this.targets);
   }

   @Override
   public boolean isFinished() {
      if (this.finished && !StorageHelper.hasRecipeMaterialsOrTarget(this.controller, this.targets)) {
         this.finished = false;
         Debug.logMessage("Invalid collect recipe \"finished\" state, resetting.");
      }

      return this.finished;
   }
}
