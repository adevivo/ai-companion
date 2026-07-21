package baritone.pathing.movement;

import baritone.api.utils.BetterBlockPos;
import baritone.pathing.movement.movements.MovementAscend;
import baritone.pathing.movement.movements.MovementDescend;
import baritone.pathing.movement.movements.MovementDiagonal;
import baritone.pathing.movement.movements.MovementDownward;
import baritone.pathing.movement.movements.MovementFall;
import baritone.pathing.movement.movements.MovementParkour;
import baritone.pathing.movement.movements.MovementPillar;
import baritone.pathing.movement.movements.MovementTraverse;
import baritone.utils.pathing.MutableMoveResult;
import net.minecraft.core.Direction;

public enum Moves {
   DOWNWARD(0, -1, 0) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         return new MovementDownward(context.getBaritone(), src, src.down());
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         this.applyOffset(x, y, z, result);
         MovementDownward.cost(context, x, y, z, result);
      }
   },
   PILLAR(0, 1, 0) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         return new MovementPillar(context.getBaritone(), src, src.up());
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         this.applyOffset(x, y, z, result);
         MovementPillar.cost(context, x, y, z, result);
      }
   },
   TRAVERSE_NORTH(0, 0, -1) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         return new MovementTraverse(context.getBaritone(), src, src.north());
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         this.applyOffset(x, y, z, result);
         MovementTraverse.cost(context, x, y, z, x, z - 1, result);
      }
   },
   TRAVERSE_SOUTH(0, 0, 1) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         return new MovementTraverse(context.getBaritone(), src, src.south());
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         this.applyOffset(x, y, z, result);
         MovementTraverse.cost(context, x, y, z, x, z + 1, result);
      }
   },
   TRAVERSE_EAST(1, 0, 0) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         return new MovementTraverse(context.getBaritone(), src, src.east());
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         this.applyOffset(x, y, z, result);
         MovementTraverse.cost(context, x, y, z, x + 1, z, result);
      }
   },
   TRAVERSE_WEST(-1, 0, 0) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         return new MovementTraverse(context.getBaritone(), src, src.west());
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         this.applyOffset(x, y, z, result);
         MovementTraverse.cost(context, x, y, z, x - 1, z, result);
      }
   },
   ASCEND_NORTH(0, 1, -1) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         return new MovementAscend(context.getBaritone(), src, new BetterBlockPos(src.x, src.y + 1, src.z - 1));
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         this.applyOffset(x, y, z, result);
         MovementAscend.cost(context, x, y, z, x, z - 1, result);
      }
   },
   ASCEND_SOUTH(0, 1, 1) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         return new MovementAscend(context.getBaritone(), src, new BetterBlockPos(src.x, src.y + 1, src.z + 1));
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         this.applyOffset(x, y, z, result);
         MovementAscend.cost(context, x, y, z, x, z + 1, result);
      }
   },
   ASCEND_EAST(1, 1, 0) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         return new MovementAscend(context.getBaritone(), src, new BetterBlockPos(src.x + 1, src.y + 1, src.z));
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         this.applyOffset(x, y, z, result);
         MovementAscend.cost(context, x, y, z, x + 1, z, result);
      }
   },
   ASCEND_WEST(-1, 1, 0) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         return new MovementAscend(context.getBaritone(), src, new BetterBlockPos(src.x - 1, src.y + 1, src.z));
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         this.applyOffset(x, y, z, result);
         MovementAscend.cost(context, x, y, z, x - 1, z, result);
      }
   },
   DESCEND_EAST(1, -1, 0, false, true) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         MutableMoveResult res = new MutableMoveResult();
         this.apply(context, src.x, src.y, src.z, res);
         return (Movement)(res.y == src.y - 1
            ? new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z))
            : new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z)));
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         MovementDescend.cost(context, x, y, z, x + 1, z, result);
      }
   },
   DESCEND_WEST(-1, -1, 0, false, true) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         MutableMoveResult res = new MutableMoveResult();
         this.apply(context, src.x, src.y, src.z, res);
         return (Movement)(res.y == src.y - 1
            ? new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z))
            : new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z)));
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         MovementDescend.cost(context, x, y, z, x - 1, z, result);
      }
   },
   DESCEND_NORTH(0, -1, -1, false, true) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         MutableMoveResult res = new MutableMoveResult();
         this.apply(context, src.x, src.y, src.z, res);
         return (Movement)(res.y == src.y - 1
            ? new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z))
            : new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z)));
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         MovementDescend.cost(context, x, y, z, x, z - 1, result);
      }
   },
   DESCEND_SOUTH(0, -1, 1, false, true) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         MutableMoveResult res = new MutableMoveResult();
         this.apply(context, src.x, src.y, src.z, res);
         return (Movement)(res.y == src.y - 1
            ? new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z))
            : new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z)));
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         MovementDescend.cost(context, x, y, z, x, z + 1, result);
      }
   },
   DIAGONAL_NORTHEAST(1, 0, -1, false, true) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         MutableMoveResult res = new MutableMoveResult();
         this.apply(context, src.x, src.y, src.z, res);
         return new MovementDiagonal(context.getBaritone(), src, Direction.NORTH, Direction.EAST, res.y - src.y);
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         MovementDiagonal.cost(context, x, y, z, x + 1, z - 1, result);
      }
   },
   DIAGONAL_NORTHWEST(-1, 0, -1, false, true) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         MutableMoveResult res = new MutableMoveResult();
         this.apply(context, src.x, src.y, src.z, res);
         return new MovementDiagonal(context.getBaritone(), src, Direction.NORTH, Direction.WEST, res.y - src.y);
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         MovementDiagonal.cost(context, x, y, z, x - 1, z - 1, result);
      }
   },
   DIAGONAL_SOUTHEAST(1, 0, 1, false, true) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         MutableMoveResult res = new MutableMoveResult();
         this.apply(context, src.x, src.y, src.z, res);
         return new MovementDiagonal(context.getBaritone(), src, Direction.SOUTH, Direction.EAST, res.y - src.y);
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         MovementDiagonal.cost(context, x, y, z, x + 1, z + 1, result);
      }
   },
   DIAGONAL_SOUTHWEST(-1, 0, 1, false, true) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         MutableMoveResult res = new MutableMoveResult();
         this.apply(context, src.x, src.y, src.z, res);
         return new MovementDiagonal(context.getBaritone(), src, Direction.SOUTH, Direction.WEST, res.y - src.y);
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         MovementDiagonal.cost(context, x, y, z, x - 1, z + 1, result);
      }
   },
   PARKOUR_NORTH(0, 0, -4, true, true) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         return MovementParkour.cost(context, src, Direction.NORTH);
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         MovementParkour.cost(context, x, y, z, Direction.NORTH, result);
      }
   },
   PARKOUR_SOUTH(0, 0, 4, true, true) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         return MovementParkour.cost(context, src, Direction.SOUTH);
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         MovementParkour.cost(context, x, y, z, Direction.SOUTH, result);
      }
   },
   PARKOUR_EAST(4, 0, 0, true, true) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         return MovementParkour.cost(context, src, Direction.EAST);
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         MovementParkour.cost(context, x, y, z, Direction.EAST, result);
      }
   },
   PARKOUR_WEST(-4, 0, 0, true, true) {
      @Override
      public Movement apply0(CalculationContext context, BetterBlockPos src) {
         return MovementParkour.cost(context, src, Direction.WEST);
      }

      @Override
      public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
         MovementParkour.cost(context, x, y, z, Direction.WEST, result);
      }
   };

   public final boolean dynamicXZ;
   public final boolean dynamicY;
   public final int xOffset;
   public final int yOffset;
   public final int zOffset;

   private Moves(int x, int y, int z, boolean dynamicXZ, boolean dynamicY) {
      this.xOffset = x;
      this.yOffset = y;
      this.zOffset = z;
      this.dynamicXZ = dynamicXZ;
      this.dynamicY = dynamicY;
   }

   private Moves(int x, int y, int z) {
      this(x, y, z, false, false);
   }

   public abstract Movement apply0(CalculationContext var1, BetterBlockPos var2);

   public abstract void apply(CalculationContext var1, int var2, int var3, int var4, MutableMoveResult var5);

   protected void applyOffset(int x, int y, int z, MutableMoveResult result) {
      if (!this.dynamicXZ && !this.dynamicY) {
         result.x = x + this.xOffset;
         result.y = y + this.yOffset;
         result.z = z + this.zOffset;
      } else {
         throw new UnsupportedOperationException();
      }
   }
}
