package adris.altoclef.player2api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;

import adris.altoclef.player2api.manager.ConversationManager;
import adris.altoclef.player2api.utils.Utils.ThrowingFunction;

public class LLMCompleter {
    private boolean isProcessing = false; // probably don't need this anymore but can keep to be safe

    private final ExecutorService llmThread = Executors.newSingleThreadExecutor();
    private static final Logger LOGGER = LogManager.getLogger();

    private <T> void process(
            Player2APIService player2apiService,
            ConversationHistory history,
            Consumer<T> extOnLLMResponse,
            Consumer<String> extOnErrMsg,
            ThrowingFunction<ConversationHistory, T> completeConversation,
            boolean isConversation) {
        LOGGER.info("Called completer.process with history={}", history);
        if (isProcessing) {
            LOGGER.warn("Called llmcompleter.process when it was already processing! This should not happen.");
            return;
        }

        // set locks:
        if (isConversation) {
            LOGGER.info("Setting conversation lock -> true");
            ConversationManager.Lock.waitingForResponseLock = true;
        }
        isProcessing = true;

        Consumer<T> onLLMResponse = resp -> {
            try {
                extOnLLMResponse.accept(resp);
            } catch (Exception e) {
                LOGGER.error(
                        "[LLMCompleter/process/onLLMResponse]: Error in external llm resp, errMsg={} llmResp={}",
                        e.getMessage(), resp.toString());
            } finally {
                LOGGER.info(
                        "Done processing, releasing conversation lock and setting this.completer.isprocessing -> false");

                isProcessing = false;
                if (isConversation) {
                    LOGGER.info("Setting conversation lock -> false");
                    ConversationManager.Lock.waitingForResponseLock = false;
                }
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
                LOGGER.info(
                        "Done processing, releasing conversation lock and setting this.completer.isprocessing -> false");
                isProcessing = false;
                if (isConversation) {
                    LOGGER.info("Setting conversation lock -> false");
                    ConversationManager.Lock.waitingForResponseLock = false;
                }
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
            Consumer<String> extOnErrMsg,
            boolean isConversation) {
        process(player2apiService, history, extOnLLMResponse, extOnErrMsg,
                player2apiService::completeConversation, isConversation);
    }

    public void processToString(
            Player2APIService player2apiService,
            ConversationHistory history,
            Consumer<String> extOnLLMResponse,
            Consumer<String> extOnErrMsg,
            boolean isConversation) {
        process(player2apiService, history, extOnLLMResponse, extOnErrMsg,
                player2apiService::completeConversationToString, isConversation);
    }

    public boolean isAvailible() {
        return !isProcessing;
    }
}