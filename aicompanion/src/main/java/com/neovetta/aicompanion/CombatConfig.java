package com.neovetta.aicompanion;

/**
 * How hard a companion hits and how much it can take. Fed from the {@code combat.*} config block;
 * overridable via system property or environment variable like {@code BehaviorConfig}.
 *
 * <p>The defaults are <em>player parity</em>, and that is the whole point of this class existing.
 * A companion is built on {@code ZombieEntity.createAttributes()}, which is a fine starting point for
 * health and movement but hands over a mob's combat numbers: 3.0 attack damage against a player's
 * 1.0, and 2.0 armour against a player's 0.0. That is triple damage bare-handed, riding on top of
 * whatever weapon it holds — a diamond sword hitting for 10.0 where the owner's hits for 8.0 — plus
 * two points of free armour. Nobody asked for it and it reads as cheating, because it is.
 *
 * <p>Raising these is a legitimate thing to want; a server running one companion against a hard
 * modpack may well want a tougher one. The rule is that it has to be asked for. Silent advantage is
 * the failure mode.
 *
 * <p>Applied per-entity by {@code CompanionEntity#applyCombatConfig()} rather than baked into the
 * registered default attribute container, because the container is built during static init before
 * any config has been read — and because per-entity application means {@code /companion reload}
 * retunes live companions instead of needing a restart.
 */
public final class CombatConfig {

    private CombatConfig() {}

    /** Vanilla {@code Player} base attack damage. The zombie default this replaces is 3.0. */
    public static final double DEFAULT_ATTACK_DAMAGE = 1.0;

    /** Vanilla {@code Player} base armour. The zombie default this replaces is 2.0. */
    public static final double DEFAULT_ARMOR = 0.0;

    /** Vanilla {@code Player} max health, which the zombie default already matches. */
    public static final double DEFAULT_MAX_HEALTH = 20.0;

    /**
     * How far the companion will look for something to fight.
     *
     * <p>16 rather than the zombie default of 35. Follow range is a mob's aggro radius, and 35 blocks
     * is most of a chunk in every direction — far enough that a companion standing still picks fights
     * with things its owner cannot see and has not noticed. 16 is a reasonable engagement distance for
     * something acting like a person.
     */
    public static final double DEFAULT_FOLLOW_RANGE = 16.0;

    /**
     * Base attack damage before any weapon. 1.0 is player parity; the zombie value was 3.0.
     */
    public static volatile double attackDamageBase =
            Double.parseDouble(resolve("aicompanion.combat.attackDamageBase",
                    "AICOMPANION_COMBAT_ATTACKDAMAGEBASE", String.valueOf(DEFAULT_ATTACK_DAMAGE)));

    /**
     * Base armour before any equipment. 0.0 is player parity; the zombie value was 2.0.
     */
    public static volatile double armorBase =
            Double.parseDouble(resolve("aicompanion.combat.armorBase",
                    "AICOMPANION_COMBAT_ARMORBASE", String.valueOf(DEFAULT_ARMOR)));

    /** Maximum health. 20.0 is player parity. */
    public static volatile double maxHealth =
            Double.parseDouble(resolve("aicompanion.combat.maxHealth",
                    "AICOMPANION_COMBAT_MAXHEALTH", String.valueOf(DEFAULT_MAX_HEALTH)));

    /** How far the companion will look for a target, in blocks. See {@link #DEFAULT_FOLLOW_RANGE}. */
    public static volatile double followRange =
            Double.parseDouble(resolve("aicompanion.combat.followRange",
                    "AICOMPANION_COMBAT_FOLLOWRANGE", String.valueOf(DEFAULT_FOLLOW_RANGE)));

    private static String resolve(String property, String env, String fallback) {
        String v = System.getProperty(property);
        if (v == null || v.isBlank()) {
            v = System.getenv(env);
        }
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
