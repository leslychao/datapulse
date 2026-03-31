package io.datapulse.etl.adapter.ozon.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonStockResponse(
        OzonStockResult result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonStockResult(
            List<OzonStockItem> items,
            @JsonProperty("last_id") String lastId
    ) {}
}
