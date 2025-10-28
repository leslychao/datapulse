package io.datapulse.marketplaces.dto.raw.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonStockRaw(
    String sku,
    String warehouse_id,
    String warehouse_name,
    Integer qty_available,
    Integer qty_reserved,
    Integer qty_in_transit,
    java.time.OffsetDateTime captured_at
) {}
