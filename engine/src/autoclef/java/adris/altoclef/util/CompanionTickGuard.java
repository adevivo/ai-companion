package adris.altoclef.util;

/**
 * Marks the window in which companion AI code is running on the server thread, so world reads made
 * during it can be answered without ever blocking.
 *
 * <p><b>The problem this solves.</b> The task engine and Baritone call {@code getBlockState} from more
 * than two hundred places, on arbitrary remembered positions. Vanilla answers a read for a missing
 * chunk by parking the calling thread on a {@code CompletableFuture} until that chunk is loaded. When
 * the caller <em>is</em> the server thread — which is also the thread that would have to run the load —
 * the server deadlocks against itself: no exception, no watchdog, just a world that never finishes
 * loading or never finishes saving.
 *
 * <p>Guarding individual call sites does not work. There are too many, and during world load and
 * shutdown chunks change state <em>while</em> a tick is running, so any check is racing. Instead the
 * chunk source itself is taught (in {@code MixinServerChunkManager}) to hand back an empty chunk
 * rather than block, but <em>only</em> while this guard is active.
 *
 * <p>Reading unloaded space as air is what the engine already expects: Baritone's
 * {@code BlockStateInterface} has always treated out-of-bounds terrain that way.
 *
 * <p>{@link ThreadLocal} rather than a plain flag on purpose — it must be impossible for this to alter
 * chunk loading for the vanilla game, another mod, or a worker thread. Only the thread inside a
 * companion tick is ever affected.
 */
public final class CompanionTickGuard {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private CompanionTickGuard() {}

    /** Open the window. Always pair with {@link #end()} in a {@code finally}. */
    public static void begin() {
        ACTIVE.set(Boolean.TRUE);
    }

    public static void end() {
        // remove() rather than set(FALSE) so the thread does not retain the entry.
        ACTIVE.remove();
    }

    /** Whether the calling thread is currently inside a companion AI tick. */
    public static boolean isActive() {
        return ACTIVE.get();
    }
}
