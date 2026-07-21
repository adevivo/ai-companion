package baritone.api.component;

import baritone.api.IBaritone;
import baritone.api.cache.IWorldProvider;
import baritone.api.selection.ISelectionManager;
import baritone.api.utils.IInteractionController;

/**
 * Lifecycle for the component caches.
 *
 * <p>Every {@link EntityComponentKey} / {@link WorldComponentKey} is a {@code static final} field, so
 * its backing map lives as long as the game process, while its keys — entity UUIDs and dimension
 * {@code ResourceKey}s — are stable across world sessions. Left alone, a component created in one
 * session is handed back in the next, still holding that session's entity and {@code Level}.
 *
 * <p>The symptoms are severe and unobvious: world lookups return no players (so a companion silently
 * ignores chat), and block reads go to a stopped server's chunk cache, which parks the server thread
 * on a future nothing will ever complete — a world that hangs on load or on save, with no exception.
 *
 * <p>{@link #clearAll()} is called on {@code SERVER_STOPPING}. Any component key added later must be
 * registered here, which is the reason this lives in one place rather than as scattered calls.
 */
public final class BaritoneComponents {

    private BaritoneComponents() {}

    /** Drop every cached component. Called when a world stops. */
    public static void clearAll() {
        IBaritone.KEY.clear();
        IInteractionController.KEY.clear();
        ISelectionManager.KEY.clear();
        IWorldProvider.KEY.clear();
    }
}
