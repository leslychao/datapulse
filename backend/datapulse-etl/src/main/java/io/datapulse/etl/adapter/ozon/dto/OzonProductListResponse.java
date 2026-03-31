package io.datapulse.etl.adapter.ozon.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonProductListResponse(
        OzonProductListResult result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonProductListResult(
            List<OzonProductListItem> items,
            int total,
            @JsonProperty("last_id") String lastId
    ) {}
}
