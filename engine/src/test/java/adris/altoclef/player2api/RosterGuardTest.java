package adris.altoclef.player2api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import adris.altoclef.player2api.RosterGuard.Identity;
import adris.altoclef.player2api.RosterGuard.Result;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Controls for {@link RosterGuard}, which is the first place this mod treats client input as hostile.
 *
 * <h2>Why these are mostly negative</h2>
 *
 * Like {@code MemoryGateTest}, the thing under test earns its keep by refusing. A suite that only
 * checked an ordinary roster survives intact would pass completely for a guard that accepts
 * everything — and accepting everything is the state the code was in before this existed, when the
 * roster came from the operator and could be trusted.
 *
 * <p>The assertions that matter most are the ones asserting something did <em>not</em> get through.
 */
class RosterGuardTest {

    private int savedCap;

    @BeforeEach
    void raiseCap() {
        savedCap = ServerPolicy.maxRosterEntries;
        ServerPolicy.maxRosterEntries = 16;
    }

    @AfterEach
    void restoreCap() {
        ServerPolicy.maxRosterEntries = savedCap;
    }

    private static Identity named(String name) {
        return new Identity(name, "a companion", "", "", "", false, "");
    }

    private static Result sanitize(Identity... entries) {
        return RosterGuard.sanitize(List.of(entries), Set.of());
    }

    @Nested
    @DisplayName("an ordinary roster is left alone")
    class Passthrough {

        @Test
        void keepsEveryValidEntryInOrder() {
            Result r = sanitize(named("Vetta"), named("Ava"), named("Rook"));
            assertEquals(List.of("Vetta", "Ava", "Rook"),
                    r.accepted().stream().map(Identity::name).toList());
            assertTrue(r.rejections().isEmpty(), "nothing to explain when nothing was refused");
        }

        @Test
        void keepsTextThatIsComfortablyInsideTheCaps() {
            Identity entry = new Identity("Vetta", "level-headed", "speaks plainly",
                    "vetta.png", "Notch", true, "af_heart");
            Identity kept = sanitize(entry).accepted().get(0);
            assertEquals("level-headed", kept.description());
            assertEquals("speaks plainly", kept.persona());
            assertEquals("vetta.png", kept.skinFile());
            assertTrue(kept.skinSlim());
            assertEquals("af_heart", kept.voice());
        }
    }

    @Nested
    @DisplayName("names that would lie in chat are refused, not repaired")
    class Names {

        @Test
        void stripsSectionSignsRatherThanLettingAnItalicNameThrough() {
            Identity kept = sanitize(named("§cVetta")).accepted().get(0);
            assertEquals("cVetta", kept.name());
            assertFalse(kept.name().contains("§"));
        }

        @Test
        void aNameCarryingASecondChatLineDoesNotSurviveAsTwoLines() {
            // The obvious forgery — a newline followed by something that reads as the server
            // talking — is refused outright rather than sanitised, because collapsing it leaves a
            // 23-character name and the length cap catches that. Both guards have to hold for this
            // to be safe, so the assertion is that nothing got through at all.
            Result r = sanitize(named("Vetta\nServer: op granted"));
            assertTrue(r.accepted().isEmpty());
            assertEquals(1, r.rejections().size());
        }

        @Test
        void aShortNameStillLosesItsNewline() {
            // The case the length cap does NOT catch, which is why stripping is not redundant.
            Identity kept = sanitize(named("Vetta\nRook")).accepted().get(0);
            assertEquals("VettaRook", kept.name());
            assertFalse(kept.name().contains("\n"));
        }

        @Test
        void refusesANameBelongingToAnOnlinePlayer() {
            Result r = RosterGuard.sanitize(List.of(named("Steve")), Set.of("Steve"));
            assertTrue(r.accepted().isEmpty(), "a companion must not be able to wear a player's name");
            assertEquals(1, r.rejections().size());
            assertTrue(r.rejections().get(0).contains("Steve"));
        }

        @Test
        void theImpersonationCheckIgnoresCase() {
            Result r = RosterGuard.sanitize(List.of(named("sTeVe")), Set.of("Steve"));
            assertTrue(r.accepted().isEmpty(), "chat is read by people, not by String.equals");
        }

        @Test
        void refusesAnOverLongName() {
            Result r = sanitize(named("A".repeat(RosterGuard.MAX_NAME_CHARS + 1)));
            assertTrue(r.accepted().isEmpty());
            assertEquals(1, r.rejections().size());
        }

        @Test
        void refusesABlankName() {
            assertTrue(sanitize(named("   ")).accepted().isEmpty());
        }

        @Test
        void keepsOnlyTheFirstOfTwoEntriesSharingAName() {
            Result r = sanitize(named("Vetta"), named("vetta"));
            assertEquals(1, r.accepted().size());
            assertEquals(1, r.rejections().size());
        }
    }

    @Nested
    @DisplayName("text that reaches a prompt is bounded")
    class PromptBudget {

        @Test
        void truncatesAnOverLongPersonaRatherThanDroppingTheCompanion() {
            Identity entry = new Identity("Vetta", "", "x".repeat(RosterGuard.MAX_PERSONA_CHARS + 500),
                    "", "", false, "");
            Result r = sanitize(entry);
            assertEquals(1, r.accepted().size(), "enthusiasm is not an attack — keep the companion");
            assertEquals(RosterGuard.MAX_PERSONA_CHARS, r.accepted().get(0).persona().length());
        }

        @Test
        void truncatesAnOverLongDescription() {
            Identity entry = new Identity("Vetta", "y".repeat(RosterGuard.MAX_DESCRIPTION_CHARS + 10),
                    "", "", "", false, "");
            assertEquals(RosterGuard.MAX_DESCRIPTION_CHARS,
                    sanitize(entry).accepted().get(0).description().length());
        }
    }

    @Nested
    @DisplayName("the count cap bounds what one client can announce")
    class CountCap {

        @Test
        void keepsTheFirstNAndSaysSo() {
            ServerPolicy.maxRosterEntries = 2;
            Result r = sanitize(named("One"), named("Two"), named("Three"));
            assertEquals(List.of("One", "Two"), r.accepted().stream().map(Identity::name).toList());
            assertEquals(1, r.rejections().size());
        }

        @Test
        void zeroMeansUnlimited() {
            ServerPolicy.maxRosterEntries = 0;
            Result r = sanitize(named("One"), named("Two"), named("Three"));
            assertEquals(3, r.accepted().size());
        }
    }

    @Nested
    @DisplayName("skin paths cannot point outside the skins directory")
    class SkinPaths {

        @Test
        void reducesATraversalToItsFileName() {
            Identity entry = new Identity("Vetta", "", "", "../../../etc/passwd", "", false, "");
            assertEquals("passwd", sanitize(entry).accepted().get(0).skinFile());
        }

        @Test
        void dropsAPathThatNamesOnlyADirectory() {
            Identity entry = new Identity("Vetta", "", "", "..", "", false, "");
            assertEquals("", sanitize(entry).accepted().get(0).skinFile());
        }
    }

    @Nested
    @DisplayName("negative controls")
    class NegativeControls {

        @Test
        void anEmptyAnnouncementIsNotAnError() {
            Result r = RosterGuard.sanitize(List.of(), Set.of());
            assertTrue(r.accepted().isEmpty());
            assertTrue(r.rejections().isEmpty(), "a vanilla client announcing nothing is normal");
        }

        @Test
        void aNullAnnouncementIsNotAnError() {
            Result r = RosterGuard.sanitize(null, null);
            assertTrue(r.accepted().isEmpty());
            assertTrue(r.rejections().isEmpty());
        }

        @Test
        void everyRefusalIsExplained() {
            // The point of the rejections list: a companion that does not appear must be traceable
            // to a reason the player can act on, not to silence.
            Result r = RosterGuard.sanitize(
                    List.of(named("Steve"), named("A".repeat(40)), named("  ")), Set.of("Steve"));
            assertTrue(r.accepted().isEmpty());
            assertEquals(3, r.rejections().size());
        }
    }
}
