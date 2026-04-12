package io.datapulse.bidding.adapter.ozon.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OzonRecommendedBidsRequest(
    @JsonProperty("sku_list") List<Long> skuList
) {}
