package io.datapulse.etl.adapter.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonActionProductDto(
        @JsonProperty("id") long productId,
        double price,
        @JsonProperty("action_price") double actionPrice,
        @JsonProperty("max_action_price") double maxActionPrice,
        @JsonProperty("add_mode") String addMode,
        int stock,
        @JsonProperty("min_stock") int minStock
) {}
