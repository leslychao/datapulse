package io.datapulse.bidding.adapter.yandex;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import io.datapulse.bidding.adapter.yandex.dto.YandexBidRecommendationsResponse;
import io.datapulse.bidding.adapter.yandex.dto.YandexBidsInfoResponse;
import io.datapulse.bidding.domain.BidReadAdapter;
import io.datapulse.bidding.domain.BidReadResult;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads current and recommended bids from Yandex Market.
 * <p>
 * Current bid: POST /v2/businesses/{businessId}/bids/info
 * Recommendations: POST /v2/businesses/{businessId}/bids/recommendations
 * <p>
 * Bid unit: percent of item cost × 100 (570 = 5.7%).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YandexBidReadAdapter implements BidReadAdapter {

  private static final String BIDS_INFO_PATH =
      "/v2/businesses/%d/bids/info";
  private static final String BIDS_RECOMMENDATIONS_PATH =
      "/v2/businesses/%d/bids/recommendations";

  private final WebClient.Builder webClientBuilder;
  private final IntegrationProperties properties;
  private final MarketplaceRateLimiter rateLimiter;

  @Override
  public BidReadResult readCurrentBid(
      String campaignExternalId,
      String marketplaceSku,
      long connectionId,
      Map<String, String> credentials) {

    String apiKey = credentials.get(CredentialKeys.YANDEX_API_KEY);
    String businessIdStr = credentials.get(CredentialKeys.YANDEX_BUSINESS_ID);

    if (apiKey == null || businessIdStr == null) {
      log.warn("Yandex bid read: missing credentials for connection={}",
          connectionId);
      return BidReadResult.empty();
    }

    if (marketplaceSku == null || marketplaceSku.isBlank()) {
      return BidReadResult.empty();
    }

    long businessId;
    try {
      businessId = Long.parseLong(businessIdStr);
    } catch (NumberFormatException e) {
      log.warn("Yandex bid read: invalid businessId={}", businessIdStr);
      return BidReadResult.empty();
    }

    Integer currentBid = fetchCurrentBid(
        businessId, marketplaceSku, connectionId, apiKey);
    BidReadResult recommendations = fetchRecommendations(
        businessId, marketplaceSku, connectionId, apiKey);

    return BidReadResult.of(
        currentBid,
        recommendations.minBid(),
        null,
        null);
  }

  @Override
  public String marketplaceType() {
    return "YANDEX";
  }

  private Integer fetchCurrentBid(
      long businessId, String offerId, long connectionId, String apiKey) {
    String baseUrl = properties.getYandex().getWriteBaseUrl();
    String endpoint = baseUrl + BIDS_INFO_PATH.formatted(businessId);

    try {
      rateLimiter.acquire(connectionId, RateLimitGroup.YANDEX_BIDS).join();

      var response = webClientBuilder.build()
          .post()
          .uri(endpoint)
          .header("Api-Key", apiKey)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of("offerIds", List.of(offerId)))
          .retrieve()
          .bodyToMono(YandexBidsInfoResponse.class)
          .block();

      rateLimiter.onResponse(
          connectionId, RateLimitGroup.YANDEX_BIDS, 200);

      if (response == null || response.result() == null
          || response.result().bids() == null) {
        return null;
      }

      return response.result().bids().stream()
          .filter(b -> offerId.equals(b.offerId()))
          .findFirst()
          .map(YandexBidsInfoResponse.BidEntry::bid)
          .orElse(null);

    } catch (WebClientResponseException e) {
      int status = e.getStatusCode().value();
      rateLimiter.onResponse(
          connectionId, RateLimitGroup.YANDEX_BIDS, status);
      log.warn("Yandex bids/info failed: offerId={}, status={}",
          offerId, status);
      return null;
    } catch (Exception e) {
      log.warn("Yandex bids/info error: offerId={}, error={}",
          offerId, e.getMessage());
      return null;
    }
  }

  private BidReadResult fetchRecommendations(
      long businessId, String offerId, long connectionId, String apiKey) {
    String baseUrl = properties.getYandex().getWriteBaseUrl();
    String endpoint =
        baseUrl + BIDS_RECOMMENDATIONS_PATH.formatted(businessId);

    try {
      rateLimiter.acquire(connectionId, RateLimitGroup.YANDEX_BIDS).join();

      var response = webClientBuilder.build()
          .post()
          .uri(endpoint)
          .header("Api-Key", apiKey)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of("offerIds", List.of(offerId)))
          .retrieve()
          .bodyToMono(YandexBidRecommendationsResponse.class)
          .block();

      rateLimiter.onResponse(
          connectionId, RateLimitGroup.YANDEX_BIDS, 200);

      if (response == null || response.result() == null
          || response.result().recommendations() == null) {
        return BidReadResult.empty();
      }

      return response.result().recommendations().stream()
          .filter(r -> offerId.equals(r.offerId()))
          .findFirst()
          .map(r -> BidReadResult.of(null, r.minBid(), null, null))
          .orElse(BidReadResult.empty());

    } catch (WebClientResponseException e) {
      int status = e.getStatusCode().value();
      rateLimiter.onResponse(
          connectionId, RateLimitGroup.YANDEX_BIDS, status);
      log.debug("Yandex bids/recommendations failed: offerId={}, "
          + "status={}", offerId, status);
      return BidReadResult.empty();
    } catch (Exception e) {
      log.debug("Yandex bids/recommendations error: offerId={}, "
          + "error={}", offerId, e.getMessage());
      return BidReadResult.empty();
    }
  }
}
