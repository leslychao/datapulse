package io.datapulse.etl.adapter.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonFinanceItem(
    String name,
    @JsonProperty("sku") long sku
) {}
