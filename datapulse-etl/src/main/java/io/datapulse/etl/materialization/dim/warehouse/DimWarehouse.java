package io.datapulse.etl.materialization.dim.warehouse;

import io.datapulse.domain.MarketplaceType;

public record DimWarehouse(
    MarketplaceType marketplace,
    String warehouseRole,
    Long warehouseId,
    String name,
    boolean active,
    boolean fbs
) {
}
