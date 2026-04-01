package io.datapulse.audit.api;

public record AlertSummaryResponse(
        long openCritical,
        long openWarning,
        long acknowledged,
        long resolvedLast7Days
) {}
