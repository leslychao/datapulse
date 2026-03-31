package io.datapulse.etl.adapter.ozon.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonPriceResponse(
        OzonPriceResult result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonPriceResult(
            List<OzonPriceItem> items,
            @JsonProperty("last_id") String lastId,
            int total
    ) {}
}
