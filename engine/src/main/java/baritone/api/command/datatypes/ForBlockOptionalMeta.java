package baritone.api.command.datatypes;

import baritone.api.command.exception.CommandException;
import baritone.api.utils.BlockOptionalMeta;
import java.util.stream.Stream;

public enum ForBlockOptionalMeta implements IDatatypeFor<BlockOptionalMeta> {
   INSTANCE;

   public BlockOptionalMeta get(IDatatypeContext ctx) throws CommandException {
      return new BlockOptionalMeta(ctx.getBaritone().getEntityContext().world(), ctx.getConsumer().getString());
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) {
      return ctx.getConsumer().tabCompleteDatatype(BlockById.INSTANCE);
   }
}
