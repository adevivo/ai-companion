package adris.altoclef.util.helpers;

import adris.altoclef.player2api.BehaviorConfig;
import baritone.api.entity.IAutomatone;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Lets hostile mobs see the companion as a target.
 *
 * <p>The companion is a {@link LivingEntity} rather than a {@code Player}, and every vanilla
 * player-hunting goal is built with a literal {@code Player.class} filter — so out of the box the
 * companion is invisible to zombies, skeletons, creepers, illagers and the rest. It could only ever be
 * attacked in retaliation, via the un-filtered {@code HurtByTargetGoal}, and then only after swinging
 * first.
 *
 * <p>Companions are identified by {@link IAutomatone}, which the engine owns. That is deliberate: the
 * companion entity class lives in the consumer mod and is not on the engine's compile path, so an
 * interface check is the only way for engine-side mixins to recognise it.
 *
 * <p>Shared by the mixins that widen targeting — see {@code CompanionTargetableMixin} for the
 * goal-based hostiles and {@code PiglinAiMixin} / {@code HoglinAiMixin} for the brain-based ones.
 */
public final class CompanionTargetHelper {
    private CompanionTargetHelper() {}

    /** Fallback search radius for mobs with no follow-range attribute to read. */
    private static final double DEFAULT_SEARCH_RANGE = 16.0;

    public static boolean isCompanion(LivingEntity entity) {
        return entity instanceof IAutomatone;
    }

    /** How far this mob looks for targets, from its own follow range. */
    public static double searchRange(LivingEntity mob) {
        if (mob == null || !mob.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE)) {
            return DEFAULT_SEARCH_RANGE;
        }

        return mob.getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    /**
     * The nearest companion this mob is allowed to attack, or null.
     *
     * <p>{@code conditions} may be null to skip the vanilla acceptability test. When supplied it is the
     * mob's own {@link TargetingConditions}, so line of sight, invisibility and any per-mob predicate
     * are applied exactly as they would be to a real player — this keeps the widening from being more
     * permissive than vanilla rather than merely wider.
     */
    public static LivingEntity nearestCompanion(Mob mob, AABB searchBox, TargetingConditions conditions) {
        if (mob == null || searchBox == null) {
            return null;
        }

        Level level = mob.level();
        if (level == null || level.isClientSide()) {
            return null;
        }

        LivingEntity best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        // Scanning LivingEntity and filtering is the only option available: getEntitiesOfClass needs a
        // Class that extends Entity, and IAutomatone is an interface on the side.
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, searchBox, CompanionTargetHelper::isCompanion);
        for (LivingEntity candidate : candidates) {
            if (!candidate.isAlive() || candidate == mob) {
                continue;
            }
            if (conditions != null && !conditions.test(mob, candidate)) {
                continue;
            }

            double distanceSq = mob.distanceToSqr(candidate);
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = candidate;
            }
        }

        return best;
    }

    /** Convenience form for brain-based mobs, which have no {@link TargetingConditions} to hand. */
    public static LivingEntity nearestCompanion(Mob mob) {
        if (!BehaviorConfig.mobsTargetCompanion || mob == null) {
            return null;
        }

        double range = searchRange(mob);
        return nearestCompanion(mob, mob.getBoundingBox().inflate(range, range / 2.0, range), null);
    }
}
