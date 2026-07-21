package adris.altoclef.multiversion.recipemanager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

public class RecipeManagerWrapper {
   private final RecipeManager recipeManager;

   public static RecipeManagerWrapper of(RecipeManager recipeManager) {
      return recipeManager == null ? null : new RecipeManagerWrapper(recipeManager);
   }

   private RecipeManagerWrapper(RecipeManager recipeManager) {
      this.recipeManager = recipeManager;
   }

   public Collection<WrappedRecipeEntry> values() {
      List<WrappedRecipeEntry> result = new ArrayList<>();

      for (ResourceLocation id : this.recipeManager.getRecipeIds().toList()) {
         result.add(new WrappedRecipeEntry(id, (Recipe<?>)this.recipeManager.byKey(id).get()));
      }

      return result;
   }
}
