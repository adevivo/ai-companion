package baritone.api.command.datatypes;

import baritone.api.command.exception.CommandException;
import baritone.api.command.helpers.TabCompleteHelper;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public enum BlockById implements IDatatypeFor<Block> {
   INSTANCE;

   public Block get(IDatatypeContext ctx) throws CommandException {
      ResourceLocation id = new ResourceLocation(ctx.getConsumer().getString());
      Block block;
      if ((block = (Block)BuiltInRegistries.BLOCK.getOptional(id).orElse(null)) == null) {
         throw new IllegalArgumentException("no block found by that id");
      } else {
         return block;
      }
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
      return new TabCompleteHelper()
         .append(BuiltInRegistries.BLOCK.keySet().stream().map(Object::toString))
         .filterPrefixNamespaced(ctx.getConsumer().getString())
         .sortAlphabetically()
         .stream();
   }
}
