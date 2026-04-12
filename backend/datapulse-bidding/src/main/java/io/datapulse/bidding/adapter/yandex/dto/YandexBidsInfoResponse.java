package io.datapulse.bidding.adapter.yandex.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexBidsInfoResponse(
    @JsonProperty("result") Result result
) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Result(
      @JsonProperty("bids") List<BidEntry> bids,
      @JsonProperty("paging") Paging paging
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record BidEntry(
      @JsonProperty("offerId") String offerId,
      @JsonProperty("bid") int bid
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Paging(
      @JsonProperty("nextPageToken") String nextPageToken
  ) {}
}
