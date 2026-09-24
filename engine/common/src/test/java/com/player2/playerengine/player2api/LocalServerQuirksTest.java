package com.player2.playerengine.player2api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.neovetta.aicompanion.core.LlmConfig;
import com.player2.playerengine.player2api.utils.HttpApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Controls for two local-server failures seen in the 2026-09 demo recordings, both of which told the
 * player something untrue or nothing at all.
 *
 * <p>LM Studio refused {@code response_format: json_object} with a 400 that reached chat as a raw
 * exception. qwen3 on Ollama returned empty replies at maxTokens=2000 and was told to raise it "to at
 * least 1000".
 */
class LocalServerQuirksTest {

    /** LM Studio's refusal, verbatim from the 2026-09-17 log. */
    private static final String LM_STUDIO_400 = "HTTP 400: Bad Request Body: "
            + "{\"error\":\"'response_format.type' must be 'json_schema' or 'text'\"}";

    private int savedMaxTokens;

    @BeforeEach
    void save() {
        savedMaxTokens = LlmConfig.maxTokens;
    }

    @AfterEach
    void restore() {
        LlmConfig.maxTokens = savedMaxTokens;
    }

    @Test
    @DisplayName("LM Studio's refusal of json_object is recognised")
    void lmStudioRefusalIsRecognised() {
        assertTrue(Player2APIService.rejectsJsonObject(new HttpApiException(LM_STUDIO_400, 400)));
    }

    @Test
    @DisplayName("other 400s, and the same words on another status, are not mistaken for it")
    void otherFailuresAreNotMistakenForIt() {
        assertFalse(Player2APIService.rejectsJsonObject(
                new HttpApiException("HTTP 400: Bad Request Body: {\"error\":\"model not found\"}", 400)));
        assertFalse(Player2APIService.rejectsJsonObject(new HttpApiException(LM_STUDIO_400, 500)),
                "a server error is not the server stating its format rules");
        assertFalse(Player2APIService.rejectsJsonObject(new Exception(LM_STUDIO_400)),
                "only an HTTP refusal counts");
    }

    @Test
    @DisplayName("the default request is json_object, which OpenRouter, xAI, OpenAI and llama.cpp take")
    void defaultFormatIsJsonObject() {
        JsonObject format = Player2APIService.responseFormat(false);
        assertEquals("json_object", format.get("type").getAsString());
        assertFalse(format.has("json_schema"));
    }

    @Test
    @DisplayName("the fallback is json_schema asking only for an object, so it asks for no more than json_object did")
    void fallbackIsAnUnconstrainedObjectSchema() {
        JsonObject format = Player2APIService.responseFormat(true);
        assertEquals("json_schema", format.get("type").getAsString());
        JsonObject jsonSchema = format.getAsJsonObject("json_schema");
        assertTrue(jsonSchema.has("name"), "LM Studio and OpenAI both require a schema name");
        JsonObject schema = jsonSchema.getAsJsonObject("schema");
        assertEquals("object", schema.get("type").getAsString());
        assertFalse(schema.has("required"),
                "memory extraction shares this path and its object has a different shape");
    }

    @Test
    @DisplayName("an empty reply at an adequate cap is named as a thinking model, not a small cap")
    void emptyReplyAtAdequateCapBlamesThinking() {
        LlmConfig.maxTokens = 2000;
        String note = Player2APIService.truncationNote("");
        assertTrue(note.contains("thinking model"), note);
        assertFalse(note.contains("at least"),
                "this is the bug: telling a 2000 cap to rise to at least 1000");
    }

    @Test
    @DisplayName("a cap below the floor is still told to rise to the floor")
    void capBelowFloorStillGetsTheFloor() {
        LlmConfig.maxTokens = 500;
        assertTrue(Player2APIService.truncationNote("{\"reason\": \"partial")
                .contains("at least " + LlmConfig.MIN_USEFUL_MAX_TOKENS));
    }

    @Test
    @DisplayName("a genuinely long reply past the floor is never advised a SMALLER number")
    void longReplyPastFloorIsAdvisedUpward() {
        LlmConfig.maxTokens = 2000;
        String note = Player2APIService.truncationNote("{\"reason\": \"a long but real answer");
        assertTrue(note.contains("above 2000"), note);
    }
}
