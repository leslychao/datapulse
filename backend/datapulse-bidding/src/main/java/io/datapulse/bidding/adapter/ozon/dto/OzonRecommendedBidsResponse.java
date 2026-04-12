package io.datapulse.bidding.adapter.ozon.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonRecommendedBidsResponse(
    @JsonProperty("recommendations") List<Recommendation> recommendations
) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Recommendation(
      @JsonProperty("sku") long sku,
      @JsonProperty("recommended_bid") BigDecimal recommendedBid
  ) {}
}
