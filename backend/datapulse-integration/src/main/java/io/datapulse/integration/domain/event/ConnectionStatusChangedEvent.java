package io.datapulse.integration.domain.event;

public record ConnectionStatusChangedEvent(
        Long connectionId,
        String oldStatus,
        String newStatus,
        String trigger
) {
}
