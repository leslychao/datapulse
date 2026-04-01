package io.datapulse.analytics.api;

import java.time.LocalDate;

public record StockHistoryResponse(
        LocalDate date,
        int available,
        Integer reserved,
        Integer warehouseId,
        String warehouseName
) {}
