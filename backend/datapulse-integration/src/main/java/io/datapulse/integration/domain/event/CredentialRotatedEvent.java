package io.datapulse.integration.domain.event;

/**
 * Published when marketplace credentials are rotated by a user.
 * No listeners yet — intended for audit_log (credential.rotate).
 * Re-validation is triggered separately via {@code ConnectionStatusChangedEvent}.
 */
public record CredentialRotatedEvent(
        Long connectionId,
        Long workspaceId,
        Long userId
) {
}
