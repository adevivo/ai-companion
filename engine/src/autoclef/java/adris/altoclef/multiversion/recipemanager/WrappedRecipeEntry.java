package adris.altoclef.multiversion.recipemanager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

public record WrappedRecipeEntry(ResourceLocation id, Recipe<?> value) {
}
