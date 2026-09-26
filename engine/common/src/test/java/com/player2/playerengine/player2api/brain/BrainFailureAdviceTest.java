package com.player2.playerengine.player2api.brain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Controls for the advice given when a client's brain fails.
 *
 * <p>The rule these enforce is narrow and was learned the hard way: <b>never name a cause that is not
 * the cause.</b> A fixed "check llm.endpoint" was sent for every failure, so a companion that hit
 * OpenRouter's free daily cap told its owner to go and debug an endpoint that was working perfectly.
 * Silence would have been better than that, and the right answer was already sitting in the error
 * text the client had sent back.
 */
class BrainFailureAdviceTest {

    private static String advise(String error) {
        return NetworkBrainTransport.adviseOn(error);
    }

    @Test
    @DisplayName("the real OpenRouter daily-cap error is named as a rate limit, not an endpoint fault")
    void openRouterDailyCapIsNamedCorrectly() {
        String observed = "adris.altoclef.player2api.utils.HttpApiException: HTTP 429: Too Many "
                + "Requests Body: {\"error\":{\"message\":\"Rate limit exceeded: free-models-per-day. "
                + "Add 10 credits to unlock 1000 free model requests per day\",\"code\":429}}";
        String advice = advise(observed);
        assertTrue(advice.contains("rate-limiting"), "it must say what actually happened");
        assertFalse(advice.contains("Check llm.endpoint"),
                "the endpoint was working; sending someone to check it is the bug this fixes");
    }

    @Test
    @DisplayName("a refused key is not reported as a rate limit")
    void authFailureIsItsOwnAdvice() {
        String advice = advise("HTTP 401: Unauthorized — invalid api key");
        assertTrue(advice.contains("apiKey") || advice.contains("key"));
        assertFalse(advice.contains("rate-limiting"));
    }

    @Test
    @DisplayName("advice that points at the config file says how to apply an edit to it")
    void configAdviceSaysHowToApply() {
        assertTrue(advise("HTTP 401: Unauthorized").contains("/companion reload"),
                "the file can already be right while the running game still holds the old key");
        assertTrue(advise("java.net.ConnectException: Connection refused").contains("/companion reload"));
        assertTrue(advise(null).contains("/companion reload"));
        assertFalse(advise("HTTP 429: Too Many Requests").contains("/companion reload"),
                "a rate limit is not fixed by editing the file, so do not suggest it is");
    }

    @Test
    @DisplayName("a server that refuses JSON mode is told where the switch is, not shown an exception")
    void jsonModeRefusalPointsAtTheToggle() {
        String advice = advise("HttpApiException: HTTP 400: Bad Request Body: "
                + "{\"error\":\"'response_format.type' must be 'json_schema' or 'text'\"}");
        assertTrue(advice.contains("JSON Mode") && advice.contains("llm.useGrammar"), advice);
        assertFalse(advice.contains("HttpApiException"), "the raw exception is what the player saw before");
    }

    @Test
    @DisplayName("an unreachable endpoint IS an endpoint problem")
    void connectionRefusedStillPointsAtTheEndpoint() {
        String advice = advise("java.net.ConnectException: Connection refused");
        assertTrue(advice.contains("llm.endpoint"),
                "this is the one case where the original message was right");
    }

    @Test
    @DisplayName("no credit is distinguished from no key")
    void outOfCreditIsItsOwnAdvice() {
        assertTrue(advise("HTTP 402: insufficient credits").contains("credit"));
    }

    @Test
    @DisplayName("an unrecognised failure is quoted rather than guessed at")
    void unknownFailureIsQuotedNotGuessed() {
        String advice = advise("something nobody has seen before");
        assertTrue(advice.contains("something nobody has seen before"),
                "an unclassified failure in the player's own words beats a confident wrong guess");
    }

    @Test
    @DisplayName("a turn the server stopped waiting for names the server's timeout, not the endpoint")
    void turnTimeoutNamesTheTimeoutKey() {
        String advice = advise(NetworkBrainTransport.timeoutReason(45_000, "brain.clientTimeoutMs"));
        assertTrue(advice.contains("brain.clientTimeoutMs") && advice.contains("45 s"), advice);
        assertFalse(advice.contains("llm.endpoint"),
                "the endpoint answered, just slowly; this is the bug the case exists to fix");
    }

    @Test
    @DisplayName("a plan timeout names the plan key, even wrapped in the planner's own error text")
    void planTimeoutNamesThePlanKey() {
        String wrapped = "your client's model could not answer ("
                + NetworkBrainTransport.timeoutReason(180_000, "brain.planTimeoutMs") + ")";
        String advice = advise(wrapped);
        assertTrue(advice.contains("brain.planTimeoutMs") && advice.contains("180 s"), advice);
        assertFalse(advice.contains("brain.clientTimeoutMs"), "that key does not govern plans");
        assertFalse(advice.contains("(("), "the raw nested reason is not repeated in chat");
    }

    @Test
    @DisplayName("an overloaded free model is named as overload, not as an endpoint fault")
    void overloadIsItsOwnAdvice() {
        String advice = advise("HTTP 503: Service Unavailable Body: {\"error\":\"model overloaded\"}");
        assertTrue(advice.contains("overloaded"), advice);
        assertFalse(advice.contains("llm.endpoint"));
    }

    @Test
    @DisplayName("a silent client says so instead of inventing a reason")
    void nullErrorAdmitsIgnorance() {
        assertTrue(advise(null).contains("did not say why"));
    }
}
