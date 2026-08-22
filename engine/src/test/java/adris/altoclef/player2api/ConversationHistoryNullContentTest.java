package adris.altoclef.player2api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression controls for the null-content crash of 2026-08-22.
 *
 * <h2>The failure</h2>
 *
 * A companion mid-build issues commands without saying anything, so the LLM reply carries a
 * {@code command} and no {@code message}. The guard in {@code AgentConversationData} is an OR, so
 * that turn stored a null message — and {@code JsonObject.addProperty(key, (String) null)} writes
 * {@link com.google.gson.JsonNull} rather than skipping the key. Nothing failed at that point.
 *
 * <p>On the <em>next</em> turn, {@code process()} logged the history, {@code toString()} called
 * {@code getAsString()} on that JsonNull, and {@code UnsupportedOperationException} came out of a
 * {@code LOGGER.info} inside the server tick loop. The world crashed on a turn where nothing was
 * wrong, because of one stored on a turn where nothing appeared to go wrong either.
 *
 * <p>Two properties are therefore worth pinning separately: nulls do not get in, and a reader
 * survives one that somehow did. Either alone would have prevented the crash; both together mean a
 * future writer that forgets cannot resurrect it.
 */
class ConversationHistoryNullContentTest {

    private static ConversationHistory fresh() {
        return new ConversationHistory("system prompt");
    }

    @Test
    @DisplayName("a command-only turn stores empty content, never a JSON null")
    void assistantMessageWithNullBecomesEmpty() {
        ConversationHistory history = fresh();
        history.addAssistantMessage(null, null);

        for (JsonObject message : history.getListJSON()) {
            assertFalse(message.get("content").isJsonNull(),
                    "a null message must be stored as empty text, not as JsonNull");
        }
    }

    @Test
    @DisplayName("every adder refuses to write a JSON null")
    void noAdderCanWriteJsonNull() {
        ConversationHistory history = fresh();
        history.addUserMessage(null, null);
        history.addSystemMessage(null, null);
        history.addAssistantMessage(null, null);
        history.setBaseSystemPrompt(null);

        for (JsonObject message : history.getListJSON()) {
            assertFalse(message.get("content").isJsonNull(), "no adder may store JsonNull");
            assertDoesNotThrow(() -> message.get("content").getAsString(),
                    "stored content must always be readable as a string");
        }
    }

    @Test
    @DisplayName("toString survives a JSON null that got in another way — it must never crash the tick loop")
    void toStringToleratesAJsonNullThatGotInAnyway() {
        ConversationHistory history = fresh();
        history.addAssistantMessage("said something", null);

        // Reproduce the poisoned state directly, as the pre-fix writer produced it.
        for (JsonObject message : history.getListJSON()) {
            message.add("content", com.google.gson.JsonNull.INSTANCE);
        }

        String dump = assertDoesNotThrow(history::toString,
                "toString is a logging helper on the server thread; it must not be able to throw");
        assertTrue(dump.contains("ConversationHistory {"), "it should still produce a usable dump");
    }

    @Test
    @DisplayName("ordinary content is unchanged")
    void normalContentStillRoundTrips() {
        ConversationHistory history = fresh();
        history.addUserMessage("where is my dog", null);
        history.addAssistantMessage("over here", null);

        String dump = history.toString();
        assertTrue(dump.contains("where is my dog"));
        assertTrue(dump.contains("over here"));
    }

    @Test
    @DisplayName("an empty message is stored as empty, not dropped — the role stays in the transcript")
    void emptyMessageKeepsItsRole() {
        ConversationHistory history = fresh();
        int before = history.getListJSON().size();
        history.addAssistantMessage("", null);
        assertEquals(before + 1, history.getListJSON().size(),
                "a silent turn is still a turn the model took");
    }
}
