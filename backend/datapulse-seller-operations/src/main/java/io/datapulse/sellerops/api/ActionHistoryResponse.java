package io.datapulse.sellerops.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ActionHistoryResponse(
    long id,
    OffsetDateTime actionDate,
    String actionType,
    String status,
    BigDecimal targetPrice,
    BigDecimal actualPrice,
    String executionMode,
    String reason,
    String initiatedBy
) {
}
