package com.player2.playerengine.multiversion.recipemanager;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;

public record WrappedRecipeEntry(Identifier id, Recipe<?> value) {
}
