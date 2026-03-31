package io.datapulse.integration.domain.event;

public record CredentialRotatedEvent(
        Long connectionId,
        Long workspaceId,
        Long userId
) {
}
