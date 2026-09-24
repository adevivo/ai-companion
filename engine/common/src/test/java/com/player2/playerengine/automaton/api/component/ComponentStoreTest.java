package com.player2.playerengine.automaton.api.component;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Controls for the rule that a component is never handed to an entity it was not built for.
 *
 * <p>The bug these guard: a parked companion restored under its old UUID received the pathfinder
 * built for its discarded body, and on a dedicated server it never moved again. The component is
 * modelled here as a record of the entity it was built for, so "whose is it" is directly checkable.
 */
class ComponentStoreTest {

    /** Stands in for a LivingEntity: a UUID that can outlive the object, and a removed flag. */
    private static final class Body {
        final UUID id;
        boolean removed;

        Body(UUID id) {
            this.id = id;
        }
    }

    private record Built(Body forBody) {}

    private static ComponentStore<Body, Built> store() {
        return new ComponentStore<>(b -> b.id, b -> b.removed, Built::new);
    }

    @Test
    @DisplayName("a body restored under its old UUID gets its own component, not the discarded body's")
    void restoredBodyGetsAFreshComponent() {
        ComponentStore<Body, Built> store = store();
        UUID ava = UUID.randomUUID();
        Body parked = new Body(ava);
        Built old = store.get(parked);

        parked.removed = true; // park: discard()
        Body restored = new Body(ava); // restore: a new object loaded from NBT, same UUID

        Built fresh = store.get(restored);
        assertNotSame(old, fresh, "this is the bug: the old component steers a body that is gone");
        assertSame(restored, fresh.forBody());
        assertSame(fresh, store.get(restored), "and it is kept from then on, not rebuilt each tick");
    }

    @Test
    @DisplayName("a stale component is reported as absent, not handed out by the non-creating lookups")
    void staleComponentIsNotPresent() {
        ComponentStore<Body, Built> store = store();
        UUID ava = UUID.randomUUID();
        Body parked = new Body(ava);
        store.get(parked);
        parked.removed = true;

        assertNull(store.getIfPresent(new Body(ava)));
    }

    @Test
    @DisplayName("two LIVE objects sharing a UUID do not destroy each other's component")
    void liveTwinsShareRatherThanThrash() {
        // Single player: the client and server copies of one entity share a UUID. Replacing on
        // identity alone would rebuild the component every time the other copy asked.
        ComponentStore<Body, Built> store = store();
        UUID ava = UUID.randomUUID();
        Body server = new Body(ava);
        Body client = new Body(ava);

        Built first = store.get(server);
        assertSame(first, store.get(client));
        assertSame(first, store.get(server));
    }

    @Test
    @DisplayName("the removed body itself can still reach its own component, so it can be shut down")
    void removedBodyStillSeesItsOwn() {
        // MixinEntity shuts pathing down from setRemoved, i.e. after the body is already removed.
        ComponentStore<Body, Built> store = store();
        Body body = new Body(UUID.randomUUID());
        Built own = store.get(body);
        body.removed = true;

        assertSame(own, store.getIfPresent(body));
    }
}
