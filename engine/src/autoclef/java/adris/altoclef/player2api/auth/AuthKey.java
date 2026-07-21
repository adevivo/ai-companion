package adris.altoclef.player2api.auth;

import java.util.UUID;

public record AuthKey(UUID playerUuid, String clientId) {}