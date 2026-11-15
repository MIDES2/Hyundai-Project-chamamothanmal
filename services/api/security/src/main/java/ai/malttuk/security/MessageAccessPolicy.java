package ai.malttuk.security;

/**
 * Recipient-only policy for reading a message or downloading its audio.
 * Both arguments must come from trusted server-side sources.
 */
public final class MessageAccessPolicy {
    private MessageAccessPolicy() {
    }

    public enum Decision {
        ALLOW,
        REQUIRE_AUTHENTICATION,
        DENY
    }

    /**
     * @param authenticatedUserId user ID obtained from verified authentication,
     *                            or null when authentication is absent/invalid
     * @param storedRecipientId recipient ID read from the stored message,
     *                          or null when the message does not exist
     */
    public static Decision evaluate(String authenticatedUserId, String storedRecipientId) {
        if (authenticatedUserId == null || authenticatedUserId.isBlank()) {
            return Decision.REQUIRE_AUTHENTICATION;
        }
        if (storedRecipientId == null || storedRecipientId.isBlank()) {
            return Decision.DENY;
        }
        // IDs are opaque and case-sensitive. Do not trim or normalize identities.
        return authenticatedUserId.equals(storedRecipientId)
                ? Decision.ALLOW
                : Decision.DENY;
    }
}
