package io.datapulse.etl.adapter.wb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbStockResponse(
        WbStockData data
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WbStockData(
            List<WbStockItem> items
    ) {}
}
