package io.datapulse.bidding.adapter.wb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WbSetBidsRequest(
    @JsonProperty("bids") List<BidItem> bids
) {

  public record BidItem(
      @JsonProperty("advert_id") long advertId,
      @JsonProperty("nm_bids") List<NmBid> nmBids
  ) {}

  public record NmBid(
      @JsonProperty("nm_id") long nmId,
      @JsonProperty("bid_kopecks") int bidKopecks,
      @JsonProperty("placement") String placement
  ) {}
}
