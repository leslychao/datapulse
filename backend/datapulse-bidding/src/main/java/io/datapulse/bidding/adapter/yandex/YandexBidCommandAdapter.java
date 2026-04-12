package io.datapulse.bidding.adapter.yandex;

import java.util.List;
import java.util.Map;

import io.datapulse.bidding.adapter.yandex.dto.YandexBidsInfoResponse;
import io.datapulse.bidding.adapter.yandex.dto.YandexSetBidsRequest;
import io.datapulse.bidding.adapter.yandex.dto.YandexSetBidsRequest.BidItem;
import io.datapulse.bidding.domain.BidActionGateway;
import io.datapulse.bidding.domain.BidActionGatewayResult;
import io.datapulse.bidding.persistence.BidActionEntity;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.CredentialKeys;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Yandex Market bid write adapter.
 * <p>
 * Write: PUT /v2/businesses/{businessId}/bids
 * Reconcile: POST /v2/businesses/{businessId}/bids/info
 * <p>
 * Bid unit: percent of item cost × 100 (570 = 5.7%).
 * Auth: Api-Key header.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YandexBidCommandAdapter implements BidActionGateway {

  private static final String BIDS_WRITE_PATH =
      "/v2/businesses/%d/bids";
  private static final String BIDS_INFO_PATH =
      "/v2/businesses/%d/bids/info";

  private final WebClient.Builder webClientBuilder;
  private final IntegrationProperties properties;
  private final MarketplaceRateLimiter rateLimiter;

  @Override
  public BidActionGatewayResult execute(BidActionEntity action,
      Map<String, String> credentials) {
    String apiKey = credentials.get(CredentialKeys.YANDEX_API_KEY);
    if (apiKey == null) {
      return BidActionGatewayResult.failure(
          "MISSING_API_KEY", "No apiKey in credentials", null);
    }

    long businessId = extractBusinessId(credentials);
    String offerId = action.getNmId();
    if (offerId == null || offerId.isBlank()) {
      return BidActionGatewayResult.failure(
          "INVALID_OFFER_ID", "nmId (offerId) is blank", null);
    }

    var request = new YandexSetBidsRequest(List.of(
        new BidItem(offerId, action.getTargetBid())));

    String baseUrl = properties.getYandex().getWriteBaseUrl();
    String endpoint = baseUrl + BIDS_WRITE_PATH.formatted(businessId);

    try {
      rateLimiter.acquire(
          action.getConnectionId(), RateLimitGroup.YANDEX_BIDS).join();

      String responseBody = webClientBuilder.build()
          .method(HttpMethod.PUT)
          .uri(endpoint)
          .header("Api-Key", apiKey)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .retrieve()
          .bodyToMono(String.class)
          .block();

      rateLimiter.onResponse(
          action.getConnectionId(), RateLimitGroup.YANDEX_BIDS, 200);

      log.info("Yandex bid set succeeded: bidActionId={}, offerId={}, "
              + "targetBid={} (pct x100), businessId={}",
          action.getId(), offerId, action.getTargetBid(), businessId);

      return BidActionGatewayResult.success(
          action.getTargetBid(), responseBody);

    } catch (WebClientResponseException e) {
      int status = e.getStatusCode().value();
      rateLimiter.onResponse(
          action.getConnectionId(), RateLimitGroup.YANDEX_BIDS, status);

      log.warn("Yandex bid set failed: bidActionId={}, offerId={}, "
              + "httpStatus={}, body={}",
          action.getId(), offerId, status,
          e.getResponseBodyAsString());

      return BidActionGatewayResult.failure(
          "HTTP_" + status,
          e.getResponseBodyAsString(),
          e.getResponseBodyAsString());

    } catch (Exception e) {
      log.error("Yandex bid set error: bidActionId={}, offerId={}, "
              + "error={}",
          action.getId(), offerId, e.getMessage(), e);

      return BidActionGatewayResult.failure(
          "INTERNAL_ERROR", e.getMessage(), null);
    }
  }

  @Override
  public BidActionGatewayResult reconcile(BidActionEntity action,
      Map<String, String> credentials) {
    String apiKey = credentials.get(CredentialKeys.YANDEX_API_KEY);
    if (apiKey == null) {
      return BidActionGatewayResult.failure(
          "MISSING_API_KEY",
          "No apiKey for Yandex reconciliation", null);
    }

    long businessId = extractBusinessId(credentials);
    String offerId = action.getNmId();

    String baseUrl = properties.getYandex().getWriteBaseUrl();
    String endpoint = baseUrl + BIDS_INFO_PATH.formatted(businessId);

    try {
      rateLimiter.acquire(
          action.getConnectionId(), RateLimitGroup.YANDEX_BIDS).join();

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
          action.getConnectionId(), RateLimitGroup.YANDEX_BIDS, 200);

      if (response == null || response.result() == null
          || response.result().bids() == null) {
        return BidActionGatewayResult.success(
            action.getTargetBid(), null);
      }

      var match = response.result().bids().stream()
          .filter(b -> offerId.equals(b.offerId()))
          .findFirst();

      if (match.isPresent()) {
        int actualBid = match.get().bid();
        if (actualBid == action.getTargetBid()) {
          return BidActionGatewayResult.success(
              actualBid, "reconciled: bid matches target");
        }
        log.warn("Yandex bid mismatch: bidActionId={}, target={}, "
                + "actual={}",
            action.getId(), action.getTargetBid(), actualBid);
        return BidActionGatewayResult.success(
            actualBid,
            "reconciled: bid mismatch target=%d actual=%d"
                .formatted(action.getTargetBid(), actualBid));
      }

      return BidActionGatewayResult.success(
          action.getTargetBid(), "reconciled: offerId not in response");

    } catch (Exception e) {
      log.warn("Yandex reconciliation failed: bidActionId={}, error={}",
          action.getId(), e.getMessage());
      return BidActionGatewayResult.success(
          action.getTargetBid(),
          "reconciliation skipped: " + e.getMessage());
    }
  }

  @Override
  public String marketplaceType() {
    return "YANDEX";
  }

  private long extractBusinessId(Map<String, String> credentials) {
    String value = credentials.get(CredentialKeys.YANDEX_BUSINESS_ID);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "Yandex businessId not found in credentials — "
              + "connection metadata may be missing");
    }
    return Long.parseLong(value);
  }
}
