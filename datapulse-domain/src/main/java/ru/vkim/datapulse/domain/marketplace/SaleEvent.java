package ru.vkim.datapulse.domain.marketplace;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SaleEvent(
        String marketplace,
        String shopId,
        String sku,
        int quantity,
        BigDecimal revenue,
        OffsetDateTime eventTime
) {}
