package io.datapulse.bidding.adapter.ozon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.List;
import java.util.Map;

import io.datapulse.bidding.adapter.ozon.dto.OzonSetBidsRequest;
import io.datapulse.bidding.adapter.ozon.dto.OzonSetBidsRequest.SkuBid;
import io.datapulse.bidding.domain.BidActionGateway;
import io.datapulse.bidding.domain.BidActionGatewayResult;
import io.datapulse.bidding.persistence.BidActionEntity;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OzonBidCommandAdapter implements BidActionGateway {

  private static final String SET_BIDS_PATH =
      "/api/client/campaign/products/set-bids";
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final WebClient.Builder webClientBuilder;
  private final IntegrationProperties properties;
  private final MarketplaceRateLimiter rateLimiter;
  private final OzonPerformanceAuthService authService;

  @Override
  public BidActionGatewayResult execute(BidActionEntity action,
      Map<String, String> credentials) {
    String clientId = credentials.get("client-id");
    String clientSecret = credentials.get("client-secret");
    if (clientId == null || clientSecret == null) {
      return BidActionGatewayResult.failure(
          "MISSING_CREDENTIALS",
          "Ozon Performance credentials require 'client-id' "
              + "and 'client-secret'",
          null);
    }

    long sku;
    try {
      sku = Long.parseLong(action.getNmId());
    } catch (NumberFormatException e) {
      return BidActionGatewayResult.failure(
          "INVALID_SKU",
          "nmId is not a valid long: " + action.getNmId(),
          null);
    }

    String bidRubles = kopecksToRubles(action.getTargetBid());

    var request = new OzonSetBidsRequest(List.of(
        new SkuBid(sku, bidRubles)));

    String baseUrl = properties.getOzon().getPerformanceBaseUrl();
    URI uri = URI.create(baseUrl + SET_BIDS_PATH);

    String accessToken;
    try {
      accessToken = authService.getAccessToken(clientId, clientSecret);
    } catch (Exception e) {
      log.error("Ozon OAuth2 token fetch failed: bidActionId={}, error={}",
          action.getId(), e.getMessage(), e);
      return BidActionGatewayResult.failure(
          "OAUTH_TOKEN_FAILED", e.getMessage(), null);
    }

    try {
      rateLimiter.acquire(
          action.getConnectionId(), RateLimitGroup.OZON_PERFORMANCE_BIDS)
          .join();

      String responseBody = webClientBuilder.build()
          .post()
          .uri(uri)
          .header("Authorization", "Bearer " + accessToken)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .retrieve()
          .bodyToMono(String.class)
          .block();

      rateLimiter.onResponse(
          action.getConnectionId(),
          RateLimitGroup.OZON_PERFORMANCE_BIDS, 200);

      log.info("Ozon bid set succeeded: bidActionId={}, sku={}, "
              + "targetBid={} kopecks ({} rubles)",
          action.getId(), sku, action.getTargetBid(), bidRubles);

      return BidActionGatewayResult.success(
          action.getTargetBid(), responseBody);

    } catch (WebClientResponseException e) {
      int status = e.getStatusCode().value();
      rateLimiter.onResponse(
          action.getConnectionId(),
          RateLimitGroup.OZON_PERFORMANCE_BIDS, status);

      if (status == 401) {
        authService.evict(clientId);
        log.warn("Ozon 401 — token evicted: bidActionId={}, sku={}",
            action.getId(), sku);
      }

      log.warn("Ozon bid set failed: bidActionId={}, sku={}, "
              + "httpStatus={}, body={}",
          action.getId(), sku, status, e.getResponseBodyAsString());

      return BidActionGatewayResult.failure(
          "HTTP_" + status,
          e.getResponseBodyAsString(),
          e.getResponseBodyAsString());

    } catch (Exception e) {
      log.error("Ozon bid set error: bidActionId={}, sku={}, error={}",
          action.getId(), sku, e.getMessage(), e);

      return BidActionGatewayResult.failure(
          "INTERNAL_ERROR", e.getMessage(), null);
    }
  }

  @Override
  public BidActionGatewayResult reconcile(BidActionEntity action,
      Map<String, String> credentials) {
    // TODO: implement read-back via POST /api/client/campaign/search/promo/products
    return BidActionGatewayResult.success(action.getTargetBid(), null);
  }

  @Override
  public String marketplaceType() {
    return "OZON";
  }

  static String kopecksToRubles(int kopecks) {
    return BigDecimal.valueOf(kopecks)
        .divide(HUNDRED, 2, RoundingMode.HALF_UP)
        .toPlainString();
  }
}
