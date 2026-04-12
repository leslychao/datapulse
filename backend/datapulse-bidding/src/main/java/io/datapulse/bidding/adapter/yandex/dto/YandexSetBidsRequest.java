package io.datapulse.bidding.adapter.yandex.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record YandexSetBidsRequest(
    @JsonProperty("bids") List<BidItem> bids
) {

  public record BidItem(
      @JsonProperty("offerId") String offerId,
      @JsonProperty("bid") int bid
  ) {}
}
