package io.datapulse.etl.domain.normalized;

public record NormalizedWarehouse(
        String externalWarehouseId,
        String name,
        String warehouseType,
        String marketplaceType
) {}
