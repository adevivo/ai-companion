package adris.altoclef.player2api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;

import adris.altoclef.player2api.utils.Utils.ThrowingFunction;

public class LLMCompleter {
    /**
     * Whether a request is out. Claimed on the server thread and released on {@link #llmThread}, so
     * it cannot be a plain field: a non-volatile read may never observe the release, which strands
     * the completer as permanently busy. {@code compareAndSet} also closes the check-then-act window
     * that the old {@code if (isProcessing) ... isProcessing = true} left open.
     */
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    private final ExecutorService llmThread = Executors.newSingleThreadExecutor();
    private static final Logger LOGGER = LogManager.getLogger();

    private <T> void process(
            Player2APIService player2apiService,
            ConversationHistory history,
            Consumer<T> extOnLLMResponse,
            Consumer<String> extOnErrMsg,
            ThrowingFunction<ConversationHistory, T> completeConversation) {
        LOGGER.info("Called completer.process with history={}", history);
        if (!inFlight.compareAndSet(false, true)) {
            LOGGER.warn("Called llmcompleter.process when it was already processing! This should not happen.");
            return;
        }

        Consumer<T> onLLMResponse = resp -> {
            try {
                extOnLLMResponse.accept(resp);
            } catch (Exception e) {
                LOGGER.error(
                        "[LLMCompleter/process/onLLMResponse]: Error in external llm resp, errMsg={} llmResp={}",
                        e.getMessage(), resp.toString());
            } finally {
                LOGGER.info("Done processing, releasing this completer back to the pool");
                inFlight.set(false);
            }
        };

        Consumer<String> onErrMsg = errMsg -> {
            try {
                extOnErrMsg.accept(errMsg);
            } catch (Exception e) {
                LOGGER.error(
                        "[LLMCompleter/process/onErrMsg]: Error in external onErrmsg, errMsgFromException={} errMsg={}",
                        e.getMessage(), errMsg);
            } finally {
                LOGGER.info("Done processing, releasing this completer back to the pool");
                inFlight.set(false);
            }
        };

        llmThread.submit(() -> {
            try {
                T response = completeConversation.apply(history);
                LOGGER.info("LLMCompleter returned json={}", response);
                onLLMResponse.accept(response);
            } catch (Exception e) {
                onErrMsg.accept(
                        e.getMessage() == null ? "Unknown error from CompleteConversation API" : e.getMessage());
            }
        });
    }

    public void processToJson(
            Player2APIService player2apiService,
            ConversationHistory history,
            Consumer<JsonObject> extOnLLMResponse,
            Consumer<String> extOnErrMsg) {
        process(player2apiService, history, extOnLLMResponse, extOnErrMsg,
                player2apiService::completeConversation);
    }

    public void processToString(
            Player2APIService player2apiService,
            ConversationHistory history,
            Consumer<String> extOnLLMResponse,
            Consumer<String> extOnErrMsg) {
        process(player2apiService, history, extOnLLMResponse, extOnErrMsg,
                player2apiService::completeConversationToString);
    }

    /**
     * Clear the in-flight flag. The pool is {@code static}, so a world that stops while a request is
     * outstanding leaves this set — {@link #isAvailible()} then returns false for the rest of the
     * game process and that pool slot is never usable again, in this world or any other. Called from
     * {@code ConversationManager.onServerStopping()}.
     */
    public void reset() {
        inFlight.set(false);
    }

    public boolean isAvailible() {
        return !inFlight.get();
    }
}