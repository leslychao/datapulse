package io.datapulse.bidding.adapter.yandex.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexBidRecommendationsResponse(
    @JsonProperty("result") Result result
) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Result(
      @JsonProperty("recommendations") List<Recommendation> recommendations,
      @JsonProperty("paging") Paging paging
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Recommendation(
      @JsonProperty("offerId") String offerId,
      @JsonProperty("bid") int bid,
      @JsonProperty("minBid") Integer minBid,
      @JsonProperty("maxBid") Integer maxBid
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Paging(
      @JsonProperty("nextPageToken") String nextPageToken
  ) {}
}
