package io.datapulse.execution.api;

import io.datapulse.execution.domain.AttemptOutcome;
import io.datapulse.execution.domain.ErrorClassification;
import io.datapulse.execution.domain.ReconciliationSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PriceActionAttemptResponse(
        long id,
        int attemptNumber,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        AttemptOutcome outcome,
        ErrorClassification errorClassification,
        String errorMessage,
        Long actorUserId,
        String providerRequest,
        String providerResponse,
        ReconciliationSource reconciliationSource,
        OffsetDateTime reconciliationReadAt,
        BigDecimal actualPrice,
        Boolean priceMatch,
        OffsetDateTime createdAt
) {
}
