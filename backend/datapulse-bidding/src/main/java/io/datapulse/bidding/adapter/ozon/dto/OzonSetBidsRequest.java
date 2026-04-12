package io.datapulse.bidding.adapter.ozon.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OzonSetBidsRequest(
    @JsonProperty("sku_bids") List<SkuBid> skuBids
) {

  public record SkuBid(
      @JsonProperty("sku") long sku,
      @JsonProperty("bid") String bid
  ) {}
}
