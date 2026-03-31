package io.datapulse.etl.adapter.wb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbPriceResponse(
        WbPriceData data,
        boolean error,
        String errorText
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WbPriceData(
            List<WbPriceGood> listGoods
    ) {}
}
