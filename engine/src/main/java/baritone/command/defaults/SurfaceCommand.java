package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IEntityContext;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.block.AirBlock;

public class SurfaceCommand extends Command {
   protected SurfaceCommand() {
      super("surface", "top");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      IEntityContext ctx = baritone.getEntityContext();
      BetterBlockPos playerPos = ctx.feetPos();
      int surfaceLevel = ctx.world().getSeaLevel();
      int worldHeight = ctx.world().getHeight();
      if (playerPos.getY() > surfaceLevel && ctx.world().getBlockState(playerPos.up()).getBlock() instanceof AirBlock) {
         this.logDirect(source, "Already at surface");
      } else {
         int startingYPos = Math.max(playerPos.getY(), surfaceLevel);

         for (int currentIteratedY = startingYPos; currentIteratedY < worldHeight; currentIteratedY++) {
            BetterBlockPos newPos = new BetterBlockPos(playerPos.getX(), currentIteratedY, playerPos.getZ());
            if (!(ctx.world().getBlockState(newPos).getBlock() instanceof AirBlock) && newPos.getY() > playerPos.getY()) {
               Goal goal = new GoalBlock(newPos.up());
               this.logDirect(source, String.format("Going to: %s", goal.toString()));
               baritone.getCustomGoalProcess().setGoalAndPath(goal);
               return;
            }
         }

         this.logDirect(source, "No higher location found");
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Used to get out of caves, mines, ...";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The surface/top command makes an entity head towards the closest surface-like area.",
         "",
         "This can be the surface or the highest available air space, depending on circumstances.",
         "",
         "Usage:",
         "> surface - Used to get out of caves, mines, ...",
         "> top - Used to get out of caves, mines, ..."
      );
   }
}
