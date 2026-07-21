package baritone.api.command.datatypes;

import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.utils.BetterBlockPos;
import java.util.stream.Stream;
import net.minecraft.util.Mth;

public enum RelativeGoalXZ implements IDatatypePost<GoalXZ, BetterBlockPos> {
   INSTANCE;

   public GoalXZ apply(IDatatypeContext ctx, BetterBlockPos origin) throws CommandException {
      if (origin == null) {
         origin = BetterBlockPos.ORIGIN;
      }

      IArgConsumer consumer = ctx.getConsumer();
      return new GoalXZ(
         Mth.floor(consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double)origin.x)),
         Mth.floor(consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double)origin.z))
      );
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) {
      IArgConsumer consumer = ctx.getConsumer();
      return consumer.hasAtMost(2) ? consumer.tabCompleteDatatype(RelativeCoordinate.INSTANCE) : Stream.empty();
   }
}
