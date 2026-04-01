package io.datapulse.analytics.api;

import java.time.OffsetDateTime;
import java.util.List;

public record DataQualityStatusResponse(
        List<SyncFreshness> syncFreshness,
        List<AutomationBlocker> automationBlockers
) {

    public record SyncFreshness(
            long connectionId,
            String connectionName,
            String sourcePlatform,
            String dataDomain,
            OffsetDateTime lastSuccessAt,
            boolean stale,
            int thresholdHours
    ) {}

    public record AutomationBlocker(
            long connectionId,
            String connectionName,
            String sourcePlatform,
            String reason,
            boolean blocked
    ) {}
}
