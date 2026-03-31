package io.datapulse.etl.domain.normalized;

public record NormalizedStockItem(
        String marketplaceSku,
        String warehouseId,
        int available,
        int reserved
) {}
