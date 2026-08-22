package adris.altoclef.player2api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Controls for {@link PlayerPreferences}, the per-player half of the trigger prefix.
 *
 * <p>The prefix decides whether a line of chat costs anybody anything, so the interesting assertions
 * are about which messages are <em>dropped</em>. The fallback case matters just as much: a vanilla
 * client announces nothing, and if that were treated as "no prefix" rather than "use the server's",
 * an operator's cost control would quietly stop applying to exactly the players least likely to have
 * configured anything.
 */
class PlayerPreferencesTest {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
    private static final UUID VANILLA = UUID.nameUUIDFromBytes("vanilla".getBytes());

    private String savedServerPrefix;

    @BeforeEach
    void isolate() {
        savedServerPrefix = BehaviorConfig.triggerPrefix;
        BehaviorConfig.triggerPrefix = "";
        PlayerPreferences.forget(ALICE);
        PlayerPreferences.forget(VANILLA);
    }

    @AfterEach
    void restore() {
        BehaviorConfig.triggerPrefix = savedServerPrefix;
        PlayerPreferences.forget(ALICE);
        PlayerPreferences.forget(VANILLA);
    }

    @Nested
    @DisplayName("a player's own prefix wins over the server's")
    class OwnPrefix {

        @Test
        void stripsTheAnnouncedPrefix() {
            PlayerPreferences.setTriggerPrefix(ALICE, "@");
            assertEquals("go and mine", PlayerPreferences.applyTriggerPrefix(ALICE, "@go and mine"));
        }

        @Test
        void dropsAMessageWithoutIt() {
            PlayerPreferences.setTriggerPrefix(ALICE, "@");
            assertNull(PlayerPreferences.applyTriggerPrefix(ALICE, "just chatting"),
                    "an unaddressed message must cost nothing");
        }

        @Test
        void dropsABarePrefix() {
            PlayerPreferences.setTriggerPrefix(ALICE, "@");
            assertNull(PlayerPreferences.applyTriggerPrefix(ALICE, "@"),
                    "an instruction to nobody is not worth a turn");
        }

        @Test
        void anEmptyAnnouncedPrefixMeansAnswerEverything() {
            // Not a fall-through to the server's value: a player who cleared their prefix meant to
            // clear it, and inheriting the operator's would silence their own companion.
            BehaviorConfig.triggerPrefix = "!";
            PlayerPreferences.setTriggerPrefix(ALICE, "");
            assertEquals("hello", PlayerPreferences.applyTriggerPrefix(ALICE, "hello"));
        }

        @Test
        void aBlankAnnouncedPrefixIsTreatedAsEmpty() {
            BehaviorConfig.triggerPrefix = "!";
            PlayerPreferences.setTriggerPrefix(ALICE, "   ");
            assertEquals("hello", PlayerPreferences.applyTriggerPrefix(ALICE, "hello"));
        }
    }

    @Nested
    @DisplayName("a client that announced nothing gets the server's rule")
    class Fallback {

        @Test
        void usesTheServerPrefix() {
            BehaviorConfig.triggerPrefix = "!";
            assertEquals("come here", PlayerPreferences.applyTriggerPrefix(VANILLA, "!come here"));
            assertNull(PlayerPreferences.applyTriggerPrefix(VANILLA, "come here"));
        }

        @Test
        void aNullSpeakerFallsBackRatherThanThrowing() {
            BehaviorConfig.triggerPrefix = "";
            assertEquals("hello", PlayerPreferences.applyTriggerPrefix(null, "hello"));
        }

        @Test
        void forgettingAPlayerRestoresTheFallback() {
            BehaviorConfig.triggerPrefix = "!";
            PlayerPreferences.setTriggerPrefix(ALICE, "");
            assertEquals("hello", PlayerPreferences.applyTriggerPrefix(ALICE, "hello"));
            PlayerPreferences.forget(ALICE);
            assertNull(PlayerPreferences.applyTriggerPrefix(ALICE, "hello"),
                    "a disconnected player must not leave their preference behind");
        }
    }

    @Nested
    @DisplayName("ServerPolicy caps")
    class Caps {

        @Test
        void zeroMeansUnlimited() {
            assertEquals(true, ServerPolicy.withinCap(9999, 0));
        }

        @Test
        void aCapAdmitsUpToButNotIncludingItself() {
            assertEquals(true, ServerPolicy.withinCap(1, 2));
            assertEquals(false, ServerPolicy.withinCap(2, 2),
                    "the count is what is already out, so at the cap the next spawn is refused");
        }
    }
}
