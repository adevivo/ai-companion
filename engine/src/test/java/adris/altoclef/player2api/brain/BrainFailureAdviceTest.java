package adris.altoclef.player2api.brain;

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
    @DisplayName("a silent client says so instead of inventing a reason")
    void nullErrorAdmitsIgnorance() {
        assertTrue(advise(null).contains("did not say why"));
    }
}
