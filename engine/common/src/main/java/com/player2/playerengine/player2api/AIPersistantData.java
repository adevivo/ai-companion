
package com.player2.playerengine.player2api;
import java.util.Deque;
import java.util.Optional;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.Event.InfoMessage;


public class AIPersistantData {
    // contains data relating to AI processing, only including data that is
    // permanent,
    // and persists across game state (not queue stuff)

    private ConversationHistory conversationHistory;
    private Character character;
    private PlayerEngineController mod;
    /** Whose file {@link #conversationHistory} reads and writes; null means the shared legacy path. */
    private java.util.UUID historyOwner;

    public AIPersistantData(PlayerEngineController mod, Character character) {
        this.character = character;
        this.mod = mod;
        this.historyOwner = mod.getOwnerUuid();
        String systemPrompt = Prompts.getAINPCSystemPrompt(character, mod.getCommandExecutor().agentCommands(), mod.getOwnerUsername());
        this.conversationHistory = new ConversationHistory(systemPrompt, character.name(),
                character.shortName(), this.historyOwner);
    }

    /**
     * Move the history to its owner's own file once the owner is known.
     *
     * <p>⚠️ The controller builds this object in its constructor, and the entity only calls
     * {@code setOwner} on the next line, so the owner was <b>always</b> null here and every
     * companion on every server used the flat legacy file {@code config/<Name>_<Name>.txt}. Two
     * players whose companions share a name (the default roster makes that the usual case) then
     * read each other's conversations into their prompts and overwrote each other's file. Found
     * 2026-09-26 on holly: {@code config/Ava_Ava.txt}, and no {@code history/} directory at all.
     *
     * <p>The shared file's contents are dropped, not adopted: whose they are cannot be known. A
     * relog with the same owner changes nothing.
     */
    public void bindHistoryToOwner(java.util.UUID owner) {
        if (owner == null || owner.equals(this.historyOwner)) {
            return;
        }
        this.historyOwner = owner;
        String systemPrompt = Prompts.getAINPCSystemPrompt(character, mod.getCommandExecutor().agentCommands(), mod.getOwnerUsername());
        this.conversationHistory = new ConversationHistory(systemPrompt, character.name(),
                character.shortName(), owner);
    }

    public void clearHistory() {
        conversationHistory.clear();
    }

    /**
     * Whether this companion and its owner have met before.
     *
     * <p>Set from the entity, which persists it in NBT. It used to be inferred from whether a
     * conversation-history file existed, which was the <em>only</em> real dependency on that file
     * surviving a restart — and it stops being answerable at all in two situations that are now
     * ordinary: history persistence turned off, and the corpus living on the owner's client, where
     * the server can see neither the file nor the memories. A flag on the companion is true wherever
     * either of those lives.
     */
    private boolean metOwner = false;

    public void setMetOwner(boolean met) {
        this.metOwner = met;
    }

    public boolean hasMetOwner() {
        return this.metOwner;
    }

    public Event getGreetingEvent() {
        String suffix = " IMPORTANT: SINCE THIS IS THE FIRST MESSAGE, ONLY USE COMMAND `bodylang greeting`";
        if (metOwner) {
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
    /** The unwrapped history, for a client that has to assemble the prompt itself. */
    public java.util.List<com.google.gson.JsonObject> rawHistory(){
        return this.conversationHistory.getListJSON();
    }

    /** A note for the model, in the transcript where the next turn will read it. */
    public void addUserMessage(String text, Player2APIService player2apiService){
        this.conversationHistory.addUserMessage(text, player2apiService);
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
    /** @return whether a history file was actually written (see {@link ConversationHistory#flush}) */
    public boolean flushHistory(){
        return this.conversationHistory.flush();
    }

    public void updateSystemPrompt(){
        String systemPrompt = Prompts.getAINPCSystemPrompt(character, mod.getCommandExecutor().agentCommands(), mod.getOwnerUsername());
        conversationHistory.setBaseSystemPrompt(systemPrompt);
    }
}