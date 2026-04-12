package io.datapulse.audit.api;

public record AlertEventFilter(
        String status,
        String severity,
        String sourcePlatform
) {
}
