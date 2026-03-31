package io.datapulse.etl.adapter.ozon.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonStockItem(
        @JsonProperty("product_id") long productId,
        @JsonProperty("offer_id") String offerId,
        List<OzonStockEntry> stocks
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonStockEntry(
            int present,
            int reserved,
            String type,
            @JsonProperty("warehouse_id") List<Long> warehouseIds,
            @JsonProperty("warehouse_name") String warehouseName
    ) {}
}
