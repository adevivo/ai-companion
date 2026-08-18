
package adris.altoclef.player2api;
import java.util.Deque;
import java.util.Optional;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.Event.InfoMessage;


public class AIPersistantData {
    // contains data relating to AI processing, only including data that is
    // permanent,
    // and persists across game state (not queue stuff)

    private ConversationHistory conversationHistory;
    private Character character;
    private AltoClefController mod;

    public AIPersistantData(AltoClefController mod, Character character) {
        this.character = character;
        this.mod = mod;
        String systemPrompt = Prompts.getAINPCSystemPrompt(character, mod.getCommandExecutor().agentCommands(), mod.getOwnerUsername());
        this.conversationHistory = new ConversationHistory(systemPrompt, character.name(), character.shortName());
    }

    public void clearHistory() {
        conversationHistory.clear();
    }

    public Event getGreetingEvent() {
        String suffix = " IMPORTANT: SINCE THIS IS THE FIRST MESSAGE, ONLY USE COMMAND `bodylang greeting`";
        if (conversationHistory.isLoadedFromFile()) {
            return (new InfoMessage("You want to welcome user back." + suffix));
        } else {
            return (new InfoMessage(character.greetingInfo() + suffix));
        }
    }

    public Event dumpEventQueueToConversationHistoryAndReturnLastEvent(Deque<Event> eventQueue, Player2APIService player2apiService){
        Event lastEvent = null;
        while(!eventQueue.isEmpty()){
            Event event = eventQueue.poll();
            conversationHistory.addUserMessage(event.getConversationHistoryString(), player2apiService);
            lastEvent = event;
        }
        return lastEvent;
    }
    public ConversationHistory getConversationHistoryWrappedWithStatus(String worldStatus, String agentStatus, String altoClefDebugMsgs, Player2APIService player2apiService, Optional<String> reminderString){
        return getConversationHistoryWrappedWithStatus(worldStatus, agentStatus, altoClefDebugMsgs,
                player2apiService, reminderString, java.util.List.of());
    }

    /** As above, plus any memories recalled for this turn. */
    public ConversationHistory getConversationHistoryWrappedWithStatus(String worldStatus, String agentStatus, String altoClefDebugMsgs, Player2APIService player2apiService, Optional<String> reminderString, java.util.List<String> memories){
        return this.conversationHistory
                .copyThenWrapLatestWithStatus(worldStatus, agentStatus, altoClefDebugMsgs, player2apiService, reminderString, memories);
    }
    public void addAssistantMessage(String llmMessage, Player2APIService player2apiService){
        this.conversationHistory.addAssistantMessage(llmMessage, player2apiService);
    }
    public Character getCharacter(){
        return this.character;
    }

    /**
     * Write this companion's conversation history to disk now.
     *
     * <p>Called at server shutdown. Quitting a singleplayer world stops its server, so this is the
     * ordinary end of every session rather than an edge case, and without it the last few messages —
     * or on a short session, all of them — never reach the file.
     */
    public void flushHistory(){
        this.conversationHistory.flush();
    }

    public void updateSystemPrompt(){
        String systemPrompt = Prompts.getAINPCSystemPrompt(character, mod.getCommandExecutor().agentCommands(), mod.getOwnerUsername());
        conversationHistory.setBaseSystemPrompt(systemPrompt);
    }
}