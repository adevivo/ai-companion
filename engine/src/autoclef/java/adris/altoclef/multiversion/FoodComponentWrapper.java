package adris.altoclef.multiversion;

import net.minecraft.world.food.FoodProperties;

public class FoodComponentWrapper {
   private final FoodProperties component;

   public static FoodComponentWrapper of(FoodProperties component) {
      return component == null ? null : new FoodComponentWrapper(component);
   }

   private FoodComponentWrapper(FoodProperties component) {
      this.component = component;
   }

   public int getHunger() {
      return this.component.getNutrition();
   }

   public float getSaturationModifier() {
      return this.component.getSaturationModifier();
   }
}
