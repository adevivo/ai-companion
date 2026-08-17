package adris.altoclef.player2api;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A durable id for one save, so a memory can say which world it belongs to.
 *
 * <h2>Why this has to be minted</h2>
 *
 * <b>Vanilla Minecraft has no stable world identity.</b> Checked against real saves on 1.20.1 —
 * the {@code UUID} tag in {@code level.dat} is the <em>player's</em>, sitting between
 * {@code DeathTime} and {@code XpTotal}. Nothing else is usable either:
 *
 * <ul>
 *   <li><b>The level name</b> is renameable, and a rename would silently orphan every memory
 *       attached to it.</li>
 *   <li><b>The seed</b> is not unique; two worlds can share one.</li>
 *   <li><b>The folder name</b> is renameable too, and is not unique across installs.</li>
 * </ul>
 *
 * The decisive case is the dedicated server: {@code level-name} in {@code server.properties}
 * defaults to literally {@code world}, so name-based identity would collide across very nearly every
 * headless server in existence.
 *
 * <p>So: a UUID, generated the first time a world is loaded with the mod installed, stored in the
 * save. Copying a world duplicates the id — the copy is treated as the same world, which is
 * arguably what a copy is.
 *
 * <h2>Where it lives</h2>
 *
 * A {@link SavedData} on the <b>overworld</b>, which puts it in {@code <save>/data/} and makes it
 * Minecraft's problem to write at the right time. One per save, not one per dimension.
 *
 * <p>Deliberately not the global config directory. {@code UnfinishedBuild} stores world-specific
 * coordinates there keyed only by companion UUID, and is safe purely because companion entity ids
 * differ between worlds — an accident rather than a design, and not one to copy.
 */
public final class WorldIdentity extends SavedData {
    private static final Logger LOGGER = LogManager.getLogger();

    /** Becomes {@code <save>/data/aicompanion_world_id.dat}. */
    private static final String DATA_NAME = "aicompanion_world_id";

    private static final String KEY_ID = "id";
    private static final String KEY_LABEL = "label";

    private final String id;
    private String label;

    private WorldIdentity(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** A brand new identity for a world being seen for the first time. */
    private static WorldIdentity fresh(String label) {
        return new WorldIdentity(UUID.randomUUID().toString(), label);
    }

    private static WorldIdentity load(CompoundTag tag) {
        String id = tag.getString(KEY_ID);
        // An empty id would silently scope every memory to "", pooling separate worlds into one.
        // Better to mint a new one and lose the old world's memories than to merge two worlds.
        if (id == null || id.isBlank()) {
            LOGGER.warn("World identity file had no id — minting a new one. Memories attached to "
                    + "the previous id, if any, will no longer be reachable in this world.");
            return fresh(tag.getString(KEY_LABEL));
        }
        return new WorldIdentity(id, tag.getString(KEY_LABEL));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putString(KEY_ID, id);
        if (label != null) {
            tag.putString(KEY_LABEL, label);
        }
        return tag;
    }

    /** The stable id. Opaque, and safe to send to the service in cleartext. */
    public String id() {
        return id;
    }

    /**
     * The world's display name at the time it was last seen — a human label, never identity.
     *
     * <p>Kept so a memory-management UI can say "Diamond Quest" instead of a GUID. It is refreshed
     * when the world is renamed, which is exactly why it cannot be the id.
     */
    public String label() {
        return label;
    }

    /**
     * The identity of the save this level belongs to, minting and storing one if this is the first
     * time the mod has seen it.
     *
     * <p>Always resolves against the overworld, so every dimension in a save shares one id.
     */
    public static WorldIdentity of(ServerLevel level) {
        MinecraftServer server = level.getServer();
        ServerLevel overworld = server.overworld();
        String name = server.getWorldData().getLevelName();

        WorldIdentity identity = overworld.getDataStorage()
                .computeIfAbsent(WorldIdentity::load, () -> {
                    WorldIdentity created = fresh(name);
                    // ⚠️ REQUIRED. computeIfAbsent registers the instance but does not mark it
                    // dirty, and SavedData is only written when it is. Without this the id is
                    // minted fresh on every single load and never reaches disk — which looks like
                    // it works, right up until every memory attached to the previous id is
                    // unreachable. Confirmed by its absence: three loads, three different ids, no
                    // aicompanion_world_id.dat anywhere.
                    created.setDirty();
                    LOGGER.info("Minted a world identity for \"{}\": {}", name, created.id);
                    return created;
                }, DATA_NAME);

        // Track renames so the label stays useful. Never touches the id.
        if (name != null && !name.equals(identity.label)) {
            identity.label = name;
            identity.setDirty();
        }
        return identity;
    }

    /** Convenience for the common case — just the id. */
    public static String idOf(ServerLevel level) {
        return of(level).id();
    }
}
