package adris.altoclef.player2api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Controls for {@link MemoryHealth}, which decides when to interrupt a player about memory.
 *
 * <h2>Why these are mostly about staying quiet</h2>
 *
 * The easy half of this class is saying something when the embedder dies; anything would do that.
 * The half with a cost is <em>not</em> saying it again — on every turn, for a feature that is
 * switched off, for one slow recall, or about a recovery from a problem the player was never told
 * about. A companion that cries wolf in the chat channel it also uses for things that matter is
 * worse than one that says nothing, because the player stops reading both.
 *
 * <p>So most of what follows asserts an empty drain. A suite that only checked that warnings appear
 * would pass completely for a class that warns on every single event, which is precisely the version
 * that must not ship.
 */
class MemoryHealthTest {

    private boolean memoryWasEnabled;

    @BeforeEach
    void setUp() {
        memoryWasEnabled = MemoryConfig.enabled;
        // Health only speaks for a feature that is meant to be running. Every test below that
        // expects a warning needs this on; the one that checks the guard turns it back off.
        MemoryConfig.enabled = true;
        MemoryHealth.rearm();
    }

    @AfterEach
    void tearDown() {
        MemoryConfig.enabled = memoryWasEnabled;
        MemoryHealth.rearm();
    }

    private static IOException refused() {
        return new IOException("Connection refused");
    }

    @Nested
    @DisplayName("silence")
    class Silence {

        @Test
        @DisplayName("a healthy process has nothing to say")
        void healthyIsSilent() {
            assertTrue(MemoryHealth.drain().isEmpty());
            assertFalse(MemoryHealth.isDegraded());
            assertEquals("healthy", MemoryHealth.summary());
        }

        @Test
        @DisplayName("memory switched off never complains, however badly it would have failed")
        void disabledNeverWarns() {
            // The default state of the whole feature. A player who has not turned memory on has
            // nothing wrong with their game and must never be told otherwise.
            MemoryConfig.enabled = false;
            MemoryHealth.embeddingsOff();
            MemoryHealth.embedFailed(refused());
            MemoryHealth.storeUnreadable(new IOException("the corpus is torn"));
            for (int i = 0; i < 10; i++) {
                MemoryHealth.recallTimedOut();
                MemoryHealth.extractionFailed(refused());
            }
            assertTrue(MemoryHealth.drain().isEmpty());
            assertFalse(MemoryHealth.isDegraded());
        }

        @Test
        @DisplayName("the same problem, over and over, is announced exactly once")
        void repeatedProblemAnnouncesOnce() {
            MemoryHealth.embedFailed(refused());
            assertEquals(1, MemoryHealth.drain().size());
            // Every subsequent turn hits the same dead endpoint. None of them is news.
            for (int i = 0; i < 20; i++) {
                MemoryHealth.embedFailed(refused());
            }
            assertTrue(MemoryHealth.drain().isEmpty());
            assertTrue(MemoryHealth.isDegraded());
        }

        @Test
        @DisplayName("a recovery nobody was warned about stays quiet")
        void unannouncedRecoveryIsSilent() {
            // The overwhelmingly common case: embeddings succeed, so embedSucceeded() runs on every
            // single call. If clearing an unraised latch spoke, a healthy session would open with
            // news of a recovery from nothing.
            for (int i = 0; i < 50; i++) {
                MemoryHealth.embedSucceeded();
                MemoryHealth.recallSucceeded();
                MemoryHealth.extractionSucceeded();
                MemoryHealth.storeLoaded();
                MemoryHealth.embeddingsOn();
            }
            assertTrue(MemoryHealth.drain().isEmpty());
        }

        @Test
        @DisplayName("draining empties the queue")
        void drainEmpties() {
            MemoryHealth.embedFailed(refused());
            assertEquals(1, MemoryHealth.drain().size());
            assertTrue(MemoryHealth.drain().isEmpty());
        }

        @Test
        @DisplayName("undelivered notices cannot pile up without bound")
        void pendingIsCapped() {
            // Nothing drains until the player next speaks to a companion, which may be never.
            for (int i = 0; i < 200; i++) {
                MemoryHealth.embedFailed(refused());
                MemoryHealth.embedSucceeded();
            }
            assertTrue(MemoryHealth.drain().size() <= 6);
        }
    }

    @Nested
    @DisplayName("counted failures")
    class Counted {

        @Test
        @DisplayName("one slow recall is not a broken feature")
        void singleTimeoutIsSilent() {
            // A cold model or a GC pause. The warm-up exists precisely so the first one is absorbed,
            // and warning here would fire on an ordinary healthy session.
            MemoryHealth.recallTimedOut();
            assertTrue(MemoryHealth.drain().isEmpty());
        }

        @Test
        @DisplayName("timeouts below the threshold stay silent, and the threshold one speaks")
        void timeoutThreshold() {
            for (int i = 0; i < MemoryHealth.FAILURES_BEFORE_WARNING - 1; i++) {
                MemoryHealth.recallTimedOut();
                assertTrue(MemoryHealth.drain().isEmpty(), "warned after " + (i + 1) + " timeout(s)");
            }
            MemoryHealth.recallTimedOut();
            assertEquals(1, MemoryHealth.drain().size());
        }

        @Test
        @DisplayName("consecutive means consecutive — a success in between resets the count")
        void successResetsTheTimeoutCount() {
            // The distinction the whole counter exists for. Occasional misses spread across a long
            // session are a working feature having a bad moment; a run of them without a single
            // success in between is a feature that has become unusable.
            for (int round = 0; round < 5; round++) {
                MemoryHealth.recallTimedOut();
                MemoryHealth.recallTimedOut();
                MemoryHealth.recallSucceeded();
            }
            assertTrue(MemoryHealth.drain().isEmpty());
        }

        @Test
        @DisplayName("one failed extraction is not a companion that has stopped learning")
        void singleExtractionFailureIsSilent() {
            MemoryHealth.extractionFailed(refused());
            assertTrue(MemoryHealth.drain().isEmpty());
        }

        @Test
        @DisplayName("a run of failed extractions is worth saying, since the player is paying for it")
        void extractionThreshold() {
            for (int i = 0; i < MemoryHealth.FAILURES_BEFORE_WARNING; i++) {
                MemoryHealth.extractionFailed(refused());
            }
            List<MemoryHealth.Notice> notices = MemoryHealth.drain();
            assertEquals(1, notices.size());
            assertTrue(notices.get(0).text().contains("/companion remember"),
                    "should say what still works: " + notices.get(0).text());
        }

        @Test
        @DisplayName("extraction that learns nothing is a success, not a failure")
        void emptyExtractionIsNotAFailure() {
            // 86% of turns hold no durable fact. If "stored nothing" counted as a failure the
            // companion would announce that it had stopped learning while working exactly as
            // designed — and the measured base rate guarantees it would happen within four turns.
            for (int i = 0; i < 100; i++) {
                MemoryHealth.extractionSucceeded();
            }
            assertTrue(MemoryHealth.drain().isEmpty());
        }
    }

    @Nested
    @DisplayName("recovery")
    class Recovery {

        @Test
        @DisplayName("an announced problem that clears says so, once")
        void recoveryIsAnnouncedOnce() {
            MemoryHealth.embedFailed(refused());
            assertEquals(1, MemoryHealth.drain().size());

            MemoryHealth.embedSucceeded();
            List<MemoryHealth.Notice> notices = MemoryHealth.drain();
            assertEquals(1, notices.size());
            assertFalse(notices.get(0).problem(), "a recovery is not a problem");

            // Every embedding after the first is not a second recovery.
            for (int i = 0; i < 20; i++) {
                MemoryHealth.embedSucceeded();
            }
            assertTrue(MemoryHealth.drain().isEmpty());
            assertFalse(MemoryHealth.isDegraded());
        }

        @Test
        @DisplayName("a problem can be announced again after it has recovered and come back")
        void problemCanRecur() {
            MemoryHealth.embedFailed(refused());
            MemoryHealth.embedSucceeded();
            assertEquals(2, MemoryHealth.drain().size());

            // A second outage later in the session is genuinely new information.
            MemoryHealth.embedFailed(refused());
            assertEquals(1, MemoryHealth.drain().size());
        }

        @Test
        @DisplayName("recall recovers on the first success after a warning")
        void recallRecovers() {
            for (int i = 0; i < MemoryHealth.FAILURES_BEFORE_WARNING; i++) {
                MemoryHealth.recallTimedOut();
            }
            assertEquals(1, MemoryHealth.drain().size());
            MemoryHealth.recallSucceeded();
            List<MemoryHealth.Notice> notices = MemoryHealth.drain();
            assertEquals(1, notices.size());
            assertFalse(notices.get(0).problem());
        }
    }

    @Nested
    @DisplayName("kinds are independent")
    class Independence {

        @Test
        @DisplayName("a working embedder does not clear an unreadable store")
        void embedSuccessDoesNotClearStoreProblem() {
            // These fail for unrelated reasons and are fixed in unrelated places. Collapsing them
            // into one "memory is broken" flag would have the first successful embedding declare the
            // player's unloadable corpus fixed, and it would still be unloadable.
            MemoryHealth.storeUnreadable(new IOException("the corpus is torn"));
            assertEquals(1, MemoryHealth.drain().size());

            MemoryHealth.embedSucceeded();
            assertTrue(MemoryHealth.drain().isEmpty());
            assertTrue(MemoryHealth.isDegraded());
            assertTrue(MemoryHealth.summary().contains("STORE_UNREADABLE"));
        }

        @Test
        @DisplayName("two problems at once produce two notices, and each clears on its own")
        void twoProblemsTrackSeparately() {
            MemoryHealth.embeddingsOff();
            MemoryHealth.storeUnreadable(new IOException("the corpus is torn"));
            assertEquals(2, MemoryHealth.drain().size());

            MemoryHealth.embeddingsOn();
            assertEquals(1, MemoryHealth.drain().size());
            assertTrue(MemoryHealth.isDegraded());

            MemoryHealth.storeLoaded();
            assertEquals(1, MemoryHealth.drain().size());
            assertFalse(MemoryHealth.isDegraded());
        }
    }

    @Nested
    @DisplayName("what the player actually reads")
    class Wording {

        @Test
        @DisplayName("a warning is flagged as a problem and a recovery is not")
        void noticesCarryTheirPolarity() {
            MemoryHealth.embeddingsOff();
            assertTrue(MemoryHealth.drain().get(0).problem());
            MemoryHealth.embeddingsOn();
            assertFalse(MemoryHealth.drain().get(0).problem());
        }

        @Test
        @DisplayName("the embeddings-off warning names the setting and the command that applies it")
        void embeddingsOffIsActionable() {
            MemoryHealth.embeddingsOff();
            String text = MemoryHealth.drain().get(0).text();
            assertTrue(text.contains("embeddings.enabled"), text);
            assertTrue(text.contains("/companion reload"), text);
        }

        @Test
        @DisplayName("the unreadable-store warning says the memories are still there")
        void storeWarningDoesNotImplyDataLoss() {
            // MemoryStore.load throws rather than discarding a torn corpus, so the file on disk is
            // intact. Telling someone their memories are gone when they are not would send them off
            // to re-teach a companion that already knows.
            MemoryHealth.storeUnreadable(new IOException("the corpus is torn"));
            String text = MemoryHealth.drain().get(0).text();
            assertTrue(text.contains("not deleted or overwritten"), text);
        }

        @Test
        @DisplayName("the unreadable-store warning never tells the reader it was THEIR store")
        void storeWarningDoesNotClaimOwnership() {
            // The latch is process-wide and this is the one kind that belongs to a single player, so
            // on a shared world whoever speaks first hears about someone else's corpus. Claiming it
            // was theirs would be a false statement delivered to the one person who cannot act on it.
            MemoryHealth.storeUnreadable(new IOException("the corpus is torn"));
            String text = MemoryHealth.drain().get(0).text();
            assertFalse(text.contains("Your"), text);
            assertFalse(text.contains("your"), text);
        }

        @Test
        @DisplayName("an async failure reports its cause, not the wrapper it arrived in")
        void detailUnwrapsCompletionException() {
            // Everything on the embedding path comes back wrapped. "CompletionException" tells the
            // player nothing; "Connection refused" tells them to start their embedder.
            MemoryHealth.embedFailed(new CompletionException(refused()));
            String text = MemoryHealth.drain().get(0).text();
            assertTrue(text.contains("Connection refused"), text);
            assertFalse(text.contains("CompletionException"), text);
        }

        @Test
        @DisplayName("a novel-length endpoint error is trimmed to something chat can hold")
        void detailIsTruncated() {
            MemoryHealth.embedFailed(new IOException("x".repeat(4000)));
            String text = MemoryHealth.drain().get(0).text();
            assertTrue(text.length() < 600, "chat line was " + text.length() + " chars");
            assertTrue(text.contains("…"), text);
        }

        @Test
        @DisplayName("a failure with no message still identifies itself")
        void detailFallsBackToTheType() {
            MemoryHealth.embedFailed(new TimeoutException());
            assertTrue(MemoryHealth.drain().get(0).text().contains("TimeoutException"));
        }
    }

    @Nested
    @DisplayName("reload")
    class Rearm {

        @Test
        @DisplayName("a problem that survives a reload is announced again")
        void rearmLetsAStillBrokenSetupWarnAgain() {
            // Someone restarts their embedder, gets the port wrong, and reloads. Without re-arming,
            // the latch from the first outage is still set, they are told nothing, and the silence
            // looks exactly like success.
            MemoryHealth.embedFailed(refused());
            assertEquals(1, MemoryHealth.drain().size());

            MemoryHealth.rearm();
            MemoryHealth.embedFailed(refused());
            assertEquals(1, MemoryHealth.drain().size());
        }

        @Test
        @DisplayName("re-arming does not itself announce anything")
        void rearmIsSilent() {
            MemoryHealth.embedFailed(refused());
            MemoryHealth.drain();
            MemoryHealth.rearm();
            assertTrue(MemoryHealth.drain().isEmpty());
            assertFalse(MemoryHealth.isDegraded());
        }
    }
}
