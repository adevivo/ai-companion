package com.player2.playerengine.automaton.api.component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Per-entity components keyed by UUID, which never hands one entity a component built for another.
 *
 * <p>Keyed by UUID because that is what the rest of Baritone looks entities up by — but a UUID
 * outlives the object it names. Parking a companion discards it; restoring it builds a new object
 * from the saved NBT, <em>with the same UUID</em>. A plain UUID map then returns the component built
 * for the discarded body, and that component holds a hard reference to it. Observed 2026-09-23 on a
 * dedicated server: after any reconnect the task runner ran, chat answered and TTS spoke, but every
 * path and every head turn was applied to the discarded body. The live one stood motionless, its
 * rotation identical to every digit across minutes, while its status line reported the dead body's
 * position.
 *
 * <p>Single player never showed it: leaving the world stops the integrated server, and that clears
 * every store. A dedicated server does not stop between reconnects, so every restore after the
 * first was stale. The same shape arises whenever vanilla rebuilds an entity under its old UUID — a
 * chunk unloading and reloading, a dimension change.
 *
 * <p>So an entry is stale when its entity has been <em>removed</em> and a different object is asking.
 * Not merely "a different object": in single player the client and server copies of an entity share
 * a UUID, and replacing on identity alone would let them take turns destroying each other's state.
 * Removal is what makes a component unusable, so removal is the test.
 *
 * <p>Minecraft-free on purpose, so the rule can be tested without bootstrapping the game.
 */
final class ComponentStore<E, C> {
    private record Entry<E, C>(E entity, C component) {}

    private final Map<UUID, Entry<E, C>> storage = new HashMap<>();
    private final Function<E, UUID> uuidOf;
    private final Predicate<E> isRemoved;
    private final Function<E, C> factory;

    ComponentStore(Function<E, UUID> uuidOf, Predicate<E> isRemoved, Function<E, C> factory) {
        this.uuidOf = uuidOf;
        this.isRemoved = isRemoved;
        this.factory = factory;
    }

    /** The live component for this entity, building a fresh one if there is none or it is stale. */
    C get(E entity) {
        UUID id = uuidOf.apply(entity);
        Entry<E, C> entry = storage.get(id);
        if (entry == null || isStaleFor(entry, entity)) {
            entry = new Entry<>(entity, factory.apply(entity));
            storage.put(id, entry);
        }
        return entry.component();
    }

    /** The live component for this entity, or null — a stale one counts as none. */
    C getIfPresent(E entity) {
        Entry<E, C> entry = storage.get(uuidOf.apply(entity));
        return entry == null || isStaleFor(entry, entity) ? null : entry.component();
    }

    void clear() {
        storage.clear();
    }

    private boolean isStaleFor(Entry<E, C> entry, E asking) {
        return entry.entity() != asking && isRemoved.test(entry.entity());
    }
}
