package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalStrictDirection;
import baritone.api.utils.IEntityContext;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class TunnelCommand extends Command {
   public TunnelCommand() {
      super("tunnel");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(3);
      IEntityContext ctx = baritone.getEntityContext();
      if (args.hasExactly(3)) {
         boolean cont = true;
         int height = Integer.parseInt(args.getArgs().get(0).getValue());
         int width = Integer.parseInt(args.getArgs().get(1).getValue());
         int depth = Integer.parseInt(args.getArgs().get(2).getValue());
         if (width < 1 || height < 2 || depth < 1 || height > 255) {
            this.logDirect(source, "Width and depth must at least be 1 block; Height must at least be 2 blocks, and cannot be greater than the build limit.");
            cont = false;
         }

         if (cont) {
            height--;
            width--;
            Direction enumFacing = ctx.entity().getDirection();
            int addition = width % 2 == 0 ? 0 : 1;
            BlockPos corner1;
            BlockPos corner2;
            switch (enumFacing) {
               case EAST:
                  corner1 = new BlockPos(ctx.feetPos().x, ctx.feetPos().y, ctx.feetPos().z - width / 2);
                  corner2 = new BlockPos(ctx.feetPos().x + depth, ctx.feetPos().y + height, ctx.feetPos().z + width / 2 + addition);
                  break;
               case WEST:
                  corner1 = new BlockPos(ctx.feetPos().x, ctx.feetPos().y, ctx.feetPos().z + width / 2 + addition);
                  corner2 = new BlockPos(ctx.feetPos().x - depth, ctx.feetPos().y + height, ctx.feetPos().z - width / 2);
                  break;
               case NORTH:
                  corner1 = new BlockPos(ctx.feetPos().x - width / 2, ctx.feetPos().y, ctx.feetPos().z);
                  corner2 = new BlockPos(ctx.feetPos().x + width / 2 + addition, ctx.feetPos().y + height, ctx.feetPos().z - depth);
                  break;
               case SOUTH:
                  corner1 = new BlockPos(ctx.feetPos().x + width / 2 + addition, ctx.feetPos().y, ctx.feetPos().z);
                  corner2 = new BlockPos(ctx.feetPos().x - width / 2, ctx.feetPos().y + height, ctx.feetPos().z + depth);
                  break;
               default:
                  throw new IllegalStateException("Unexpected value: " + enumFacing);
            }

            this.logDirect(source, String.format("Creating a tunnel %s block(s) high, %s block(s) wide, and %s block(s) deep", height + 1, width + 1, depth));
            baritone.getBuilderProcess().clearArea(corner1, corner2);
         }
      } else {
         Goal goal = new GoalStrictDirection(ctx.feetPos(), ctx.entity().getDirection());
         baritone.getCustomGoalProcess().setGoalAndPath(goal);
         this.logDirect(source, String.format("Goal: %s", goal.toString()));
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Set a goal to tunnel in your current direction";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The tunnel command sets a goal that tells Automatone to mine completely straight in the direction that you're facing.",
         "",
         "Usage:",
         "> tunnel - No arguments, mines in a 1x2 radius.",
         "> tunnel <height> <width> <depth> - Tunnels in a user defined height, width and depth."
      );
   }
}
