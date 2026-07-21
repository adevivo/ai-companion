package baritone.api.command.datatypes;

import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.utils.BetterBlockPos;
import java.util.stream.Stream;

public enum RelativeBlockPos implements IDatatypePost<BetterBlockPos, BetterBlockPos> {
   INSTANCE;

   public BetterBlockPos apply(IDatatypeContext ctx, BetterBlockPos origin) throws CommandException {
      if (origin == null) {
         origin = BetterBlockPos.ORIGIN;
      }

      IArgConsumer consumer = ctx.getConsumer();
      return new BetterBlockPos(
         consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double)origin.x),
         consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double)origin.y),
         consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double)origin.z)
      );
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
      IArgConsumer consumer = ctx.getConsumer();
      if (consumer.hasAny() && !consumer.has(4)) {
         while (consumer.has(2) && consumer.peekDatatypeOrNull(RelativeCoordinate.INSTANCE) != null) {
            consumer.get();
         }

         return consumer.tabCompleteDatatype(RelativeCoordinate.INSTANCE);
      } else {
         return Stream.empty();
      }
   }
}
