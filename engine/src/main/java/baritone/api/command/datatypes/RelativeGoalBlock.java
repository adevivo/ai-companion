package baritone.api.command.datatypes;

import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import java.util.stream.Stream;
import net.minecraft.util.Mth;

public enum RelativeGoalBlock implements IDatatypePost<GoalBlock, BetterBlockPos> {
   INSTANCE;

   public GoalBlock apply(IDatatypeContext ctx, BetterBlockPos origin) throws CommandException {
      if (origin == null) {
         origin = BetterBlockPos.ORIGIN;
      }

      IArgConsumer consumer = ctx.getConsumer();
      return new GoalBlock(
         Mth.floor(consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double)origin.x)),
         Mth.floor(consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double)origin.y)),
         Mth.floor(consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double)origin.z))
      );
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) {
      IArgConsumer consumer = ctx.getConsumer();
      return consumer.hasAtMost(3) ? consumer.tabCompleteDatatype(RelativeCoordinate.INSTANCE) : Stream.empty();
   }
}
