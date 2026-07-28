package baritone.utils;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A {@link BlockPlaceContext} for an entity that is not a {@link net.minecraft.world.entity.player.Player}.
 *
 * <p>This fork drives Baritone from a plain {@code LivingEntity}, and the places that ask a block
 * "what state would you be if I placed you here?" were left passing {@code null} where vanilla wants
 * the player. Most blocks never look, so it went unnoticed for a long time. Blocks that orient
 * themselves towards whoever placed them do look, and they dereference it without a null check:
 *
 * <pre>
 *   NullPointerException: Cannot invoke "Entity.getViewXRot(float)" because "$$0" is null
 *       at Direction.orderedByNearest
 *       at BlockPlaceContext.getNearestLookingDirections
 *       at StairBlock.getStateForPlacement
 *       at BuilderProcess.approxPlaceable
 * </pre>
 *
 * <p>That escaped into {@code ServerLevel.tickNonPassenger}, which Minecraft treats as fatal, and
 * closed the player's world. It became reachable the moment the companion could obtain stairs — it was
 * holding 52 of them — but nothing about it is specific to stairs; doors, fences, anvils, chests,
 * furnaces and every other orientable block ask the same question.
 *
 * <p>The fix is not a guard. {@link Direction#orderedByNearest} takes an {@link Entity}, not a Player,
 * and the companion is an Entity — so every method that wanted the player's aim can simply be answered
 * with the companion's own. Placement stops crashing <em>and</em> starts coming out facing the right
 * way, which is what the callers always assumed: {@code BuilderProcess} sets the entity's yaw and pitch
 * either side of one of these calls, code that has done nothing at all since the player was nulled out.
 */
public class EntityPlaceContext extends BlockPlaceContext {

    private final Entity entity;

    public EntityPlaceContext(Entity entity, Level level, InteractionHand hand, ItemStack stack,
            BlockHitResult hitResult) {
        super(new UseOnContext(level, null, hand, stack, hitResult) {
            @Override
            public boolean isSecondaryUseActive() {
                // The bot never sneaks to place, and vanilla would have asked the null player.
                return false;
            }
        });
        this.entity = entity;
    }

    /**
     * Vanilla's implementation, with the companion in the player's place.
     *
     * <p>The reordering is copied rather than delegated: when the context is not replacing the block
     * that was clicked, the face opposite the clicked one is promoted to the front so a block placed
     * against a wall attaches to that wall instead of to wherever the entity happens to be looking.
     * Dropping that would place attachable blocks on the wrong side.
     */
    @Override
    public Direction[] getNearestLookingDirections() {
        Direction[] directions = Direction.orderedByNearest(entity);
        if (replaceClicked) {
            return directions;
        }
        Direction opposite = getClickedFace().getOpposite();
        int index = 0;
        while (index < directions.length && directions[index] != opposite) {
            index++;
        }
        if (index > 0) {
            System.arraycopy(directions, 0, directions, 1, index);
            directions[0] = opposite;
        }
        return directions;
    }

    @Override
    public Direction getNearestLookingDirection() {
        return Direction.orderedByNearest(entity)[0];
    }

    @Override
    public Direction getNearestLookingVerticalDirection() {
        return Direction.getFacingAxis(entity, Direction.Axis.Y);
    }

    /**
     * Which way the placer is facing. Vanilla is null-safe here but answers {@code NORTH} for a null
     * player, so this was not crashing — it was quietly placing everything facing north.
     */
    @Override
    public Direction getHorizontalDirection() {
        return entity.getDirection();
    }

    /** As {@link #getHorizontalDirection()}: null-safe in vanilla, but a constant {@code 0} degrees. */
    @Override
    public float getRotation() {
        return entity.getYRot();
    }
}
