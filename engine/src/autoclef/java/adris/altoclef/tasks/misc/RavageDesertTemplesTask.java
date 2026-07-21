package adris.altoclef.tasks.misc;

import adris.altoclef.tasks.movement.SearchWithinBiomeTask;
import adris.altoclef.tasks.squashed.CataloguedResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;

public class RavageDesertTemplesTask extends Task {
   public final Item[] LOOT = new Item[]{
      Items.BONE,
      Items.ROTTEN_FLESH,
      Items.GUNPOWDER,
      Items.SAND,
      Items.STRING,
      Items.SPIDER_EYE,
      Items.ENCHANTED_BOOK,
      Items.SADDLE,
      Items.GOLDEN_APPLE,
      Items.GOLD_INGOT,
      Items.IRON_INGOT,
      Items.EMERALD,
      Items.IRON_HORSE_ARMOR,
      Items.GOLDEN_HORSE_ARMOR,
      Items.DIAMOND,
      Items.DIAMOND_HORSE_ARMOR,
      Items.ENCHANTED_GOLDEN_APPLE
   };
   private BlockPos currentTemple;
   private Task lootTask;
   private Task pickaxeTask;

   @Override
   protected void onStart() {
      this.controller.getBehaviour().push();
   }

   @Override
   protected Task onTick() {
      if (this.pickaxeTask != null && !this.pickaxeTask.isFinished()) {
         this.setDebugState("Need to get pickaxes first");
         return this.pickaxeTask;
      } else if (this.lootTask != null && !this.lootTask.isFinished()) {
         this.setDebugState("Looting found temple");
         return this.lootTask;
      } else if (StorageHelper.miningRequirementMetInventory(this.controller, MiningRequirement.WOOD)) {
         this.setDebugState("Need to get pickaxes first");
         this.pickaxeTask = new CataloguedResourceTask(new ItemTarget(Items.WOODEN_PICKAXE, 2));
         return this.pickaxeTask;
      } else {
         this.currentTemple = WorldHelper.getADesertTemple(this.controller);
         if (this.currentTemple != null) {
            this.lootTask = new LootDesertTempleTask(this.currentTemple, List.of(this.LOOT));
            this.setDebugState("Looting found temple");
            return this.lootTask;
         } else {
            return new SearchWithinBiomeTask(Biomes.DESERT);
         }
      }
   }

   @Override
   protected void onStop(Task task) {
      this.controller.getBehaviour().pop();
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof RavageDesertTemplesTask;
   }

   @Override
   public boolean isFinished() {
      return false;
   }

   @Override
   protected String toDebugString() {
      return "Ravaging Desert Temples";
   }
}
