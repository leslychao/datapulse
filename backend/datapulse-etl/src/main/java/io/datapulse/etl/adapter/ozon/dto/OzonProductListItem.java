package io.datapulse.etl.adapter.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonProductListItem(
        @JsonProperty("product_id") long productId,
        @JsonProperty("offer_id") String offerId
) {}
