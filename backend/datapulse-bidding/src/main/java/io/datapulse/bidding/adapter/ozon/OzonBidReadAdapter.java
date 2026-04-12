package io.datapulse.bidding.adapter.ozon;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import io.datapulse.bidding.adapter.ozon.dto.OzonRecommendedBidsRequest;
import io.datapulse.bidding.adapter.ozon.dto.OzonRecommendedBidsResponse;
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
public class OzonBidReadAdapter {

  private static final String RECOMMENDED_BIDS_PATH =
      "/api/client/campaign/products/recommended-bids";

  private final WebClient.Builder webClientBuilder;
  private final IntegrationProperties properties;
  private final MarketplaceRateLimiter rateLimiter;

  /**
   * Fetches recommended bids for a list of SKUs.
   * Response bids are in rubles; caller is responsible for
   * converting to kopecks if needed.
   *
   * @param skuIds      Ozon SKU IDs
   * @param accessToken OAuth2 bearer token
   * @param connectionId marketplace connection ID for rate limiting
   * @return recommendations if available
   */
  public Optional<OzonRecommendedBidsResponse> getRecommendedBids(
      List<Long> skuIds, String accessToken, long connectionId) {
    String baseUrl = properties.getOzon().getPerformanceBaseUrl();
    URI uri = URI.create(baseUrl + RECOMMENDED_BIDS_PATH);

    var request = new OzonRecommendedBidsRequest(skuIds);

    try {
      rateLimiter.acquire(connectionId, RateLimitGroup.OZON_PERFORMANCE_BIDS)
          .join();

      OzonRecommendedBidsResponse response = webClientBuilder.build()
          .post()
          .uri(uri)
          .header("Authorization", "Bearer " + accessToken)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .retrieve()
          .bodyToMono(OzonRecommendedBidsResponse.class)
          .block();

      rateLimiter.onResponse(
          connectionId, RateLimitGroup.OZON_PERFORMANCE_BIDS, 200);

      log.debug("Ozon recommended bids fetched: skuCount={}", skuIds.size());
      return Optional.ofNullable(response);

    } catch (WebClientResponseException e) {
      int status = e.getStatusCode().value();
      rateLimiter.onResponse(
          connectionId, RateLimitGroup.OZON_PERFORMANCE_BIDS, status);

      if (status == 400 || status == 404) {
        log.debug("Ozon recommended bids not available: skuCount={}, "
            + "status={}", skuIds.size(), status);
        return Optional.empty();
      }

      log.warn("Ozon recommended bids fetch failed: skuCount={}, "
              + "status={}, body={}",
          skuIds.size(), status, e.getResponseBodyAsString());
      throw e;

    } catch (Exception e) {
      log.error("Ozon recommended bids error: skuCount={}, error={}",
          skuIds.size(), e.getMessage(), e);
      throw e;
    }
  }
}
