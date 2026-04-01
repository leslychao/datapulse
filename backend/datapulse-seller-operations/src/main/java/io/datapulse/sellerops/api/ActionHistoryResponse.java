package io.datapulse.sellerops.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ActionHistoryResponse(
    long actionId,
    String status,
    String executionMode,
    BigDecimal targetPrice,
    BigDecimal currentPriceAtCreation,
    String cancelReason,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
