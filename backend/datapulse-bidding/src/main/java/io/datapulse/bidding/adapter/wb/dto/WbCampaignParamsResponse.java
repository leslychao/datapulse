package io.datapulse.bidding.adapter.wb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WbCampaignParamsResponse(
    @JsonProperty("nms") List<NmEntry> nms
) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record NmEntry(
      @JsonProperty("nm_id") long nmId,
      @JsonProperty("bids") BidInfo bids
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record BidInfo(
      @JsonProperty("competitiveBid") int competitiveBid,
      @JsonProperty("leadersBid") int leadersBid,
      @JsonProperty("topBid") int topBid
  ) {}
}
