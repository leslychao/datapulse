package io.datapulse.etl.adapter.ozon.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonProductInfoResponse(
        OzonProductInfoResult result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OzonProductInfoResult(
            List<OzonProductInfo> items
    ) {}
}
