package adris.altoclef.player2api;

public sealed interface Event // tagged union basically of the below events
        permits Event.UserMessage, Event.CharacterMessage, Event.InfoMessage {
    String message();

    public String getConversationHistoryString();

    /**
     * Something a player said.
     *
     * @param broadcast whether the same line went to every companion in range, rather than to this one
     *                  alone. Recorded on the event rather than only in the reminder because it stays
     *                  in the conversation history: a companion looking back at "team up and fight"
     *                  needs to know the other one was told too, or it reads as a solo instruction.
     */
    public record UserMessage(String message, String userName, boolean broadcast) implements Event {
        public UserMessage(String message, String userName) {
            this(message, userName, false);
        }

        public String getConversationHistoryString() {
            return broadcast
                    ? String.format("User Message [%s] (said to ALL companions at once, not just you): %s",
                            userName, message)
                    : String.format("User Message: [%s]: %s", userName, message);
        }

        public String toString() {
            return String.format("UserMessage(userName='%s', message='%s', broadcast=%s)",
                    userName, message, broadcast);
        }
    }

    public record InfoMessage(String message) implements Event {
        public String getConversationHistoryString() {
            return String.format("Info: %s", message);
        }

        public String toString() {
            return getConversationHistoryString();
        }
    }

    public record CharacterMessage(String message, String command, AgentConversationData sendingCharacterData)
            implements Event {
        public String getConversationHistoryString() {
            return String.format("Other AI Message: [%s]: %s", sendingCharacterData.getName(), message);
        }

        public String toString() {
            return String.format("CharacterMessage(name='%s', message='%s', command='%s')",
                    sendingCharacterData.getName(), message, command);
        }

    }
}
