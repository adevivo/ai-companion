package baritone.api.command.datatypes;

import baritone.api.command.exception.CommandException;
import baritone.api.command.helpers.TabCompleteHelper;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public enum EntityClassById implements IDatatypeFor<EntityType<?>> {
   INSTANCE;

   public EntityType<?> get(IDatatypeContext ctx) throws CommandException {
      ResourceLocation id = new ResourceLocation(ctx.getConsumer().getString());
      EntityType<?> entity;
      if ((entity = (EntityType<?>)BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null)) == null) {
         throw new IllegalArgumentException("no entity found by that id");
      } else {
         return entity;
      }
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
      return new TabCompleteHelper()
         .append(BuiltInRegistries.ENTITY_TYPE.stream().map(Object::toString))
         .filterPrefixNamespaced(ctx.getConsumer().getString())
         .sortAlphabetically()
         .stream();
   }
}
