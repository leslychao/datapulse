package io.datapulse.etl.adapter.ozon.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonFinanceResponse(
        OzonFinanceResult result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonFinanceResult(
            List<OzonFinanceTransaction> operations,
            @JsonProperty("page_count") int pageCount,
            @JsonProperty("row_count") int rowCount
    ) {}
}
