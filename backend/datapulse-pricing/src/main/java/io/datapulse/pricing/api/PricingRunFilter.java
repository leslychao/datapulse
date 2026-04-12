package io.datapulse.pricing.api;

import io.datapulse.pricing.domain.RunStatus;
import io.datapulse.pricing.domain.RunTriggerType;

import java.time.OffsetDateTime;
import java.util.List;

public record PricingRunFilter(
        Long connectionId,
        List<RunStatus> status,
        List<RunTriggerType> triggerType,
        OffsetDateTime from,
        OffsetDateTime to
) {
}
