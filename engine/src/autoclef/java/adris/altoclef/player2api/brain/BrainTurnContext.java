package adris.altoclef.player2api.brain;

import java.util.UUID;

/**
 * Everything a brain turn needs to know that is not the prompt itself.
 *
 * <p>Resolved on the <b>server thread</b> by {@code AgentConversationData.process}, before any async
 * work starts, because several of these cannot be read anywhere else: {@code WorldIdentity} touches
 * {@code SavedData}, and the owner reference goes stale the moment a player reconnects.
 *
 * @param companionUuid whose brain this is
 * @param ownerUuid     the player the companion belongs to, or null if not resolvable this turn
 * @param companionName the companion's display name, used as the retrieval companion id
 * @param ownerName     the owner's username, used as the subject of their own extracted facts
 * @param turnText      what the player actually said, or <b>null</b> on a self-prompted turn
 * @param worldId       the current save's minted id, or null if it could not be resolved
 * @param autonomous    true when the companion prompted itself rather than being spoken to
 */
public record BrainTurnContext(
        UUID companionUuid,
        UUID ownerUuid,
        String companionName,
        String ownerName,
        String turnText,
        String worldId,
        boolean autonomous) {

    /**
     * Whether this turn is one a player drove, with an owner and a message to work from.
     *
     * <p>Both memory paths gate on exactly this and must agree: recall against an InfoMessage ranks
     * memories about nothing, and extraction over the companion's own self-prompt is the closed loop
     * the grounding guard exists to prevent.
     */
    public boolean isPlayerDriven() {
        return !autonomous && ownerUuid != null && turnText != null && !turnText.isBlank();
    }
}
