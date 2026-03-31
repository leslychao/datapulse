package io.datapulse.integration.domain.event;

public record CredentialAccessedEvent(
        Long connectionId,
        Long workspaceId,
        String purpose
) {
}
