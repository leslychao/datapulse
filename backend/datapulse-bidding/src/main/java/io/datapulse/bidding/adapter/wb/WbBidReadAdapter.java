package io.datapulse.bidding.adapter.wb;

import java.net.URI;
import java.util.Optional;

import io.datapulse.bidding.adapter.wb.dto.WbCampaignParamsResponse;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class WbBidReadAdapter {

  private static final String RECOMMENDATIONS_PATH =
      "/api/advert/v0/bids/recommendations";

  private final WebClient.Builder webClientBuilder;
  private final IntegrationProperties properties;
  private final MarketplaceRateLimiter rateLimiter;

  /**
   * Fetches recommended bids for a specific product in a campaign.
   * WB API is per-item: one product per request.
   *
   * @param campaignId WB campaign (advert) ID
   * @param nmId       WB product article (nmId)
   * @param connectionId marketplace connection ID for rate limiting
   * @param apiToken   WB API key
   * @return recommendations if available, empty if campaign/product not found
   */
  public Optional<WbCampaignParamsResponse> getRecommendedBids(
      long campaignId, long nmId, long connectionId, String apiToken) {
    String baseUrl = properties.getWildberries().getAdvertBaseUrl();

    URI uri = UriComponentsBuilder.fromUriString(baseUrl + RECOMMENDATIONS_PATH)
        .queryParam("advertId", campaignId)
        .queryParam("nmId", nmId)
        .build()
        .toUri();

    try {
      rateLimiter.acquire(connectionId, RateLimitGroup.WB_ADVERT_RECOMMENDATIONS)
          .join();

      WbCampaignParamsResponse response = webClientBuilder.build()
          .get()
          .uri(uri)
          .header("Authorization", apiToken)
          .retrieve()
          .bodyToMono(WbCampaignParamsResponse.class)
          .block();

      rateLimiter.onResponse(
          connectionId, RateLimitGroup.WB_ADVERT_RECOMMENDATIONS, 200);

      log.debug("WB bid recommendations fetched: campaignId={}, nmId={}",
          campaignId, nmId);

      return Optional.ofNullable(response);

    } catch (WebClientResponseException e) {
      int status = e.getStatusCode().value();
      rateLimiter.onResponse(
          connectionId, RateLimitGroup.WB_ADVERT_RECOMMENDATIONS, status);

      if (status == 400 || status == 404) {
        log.debug("WB bid recommendations not available: campaignId={}, "
                + "nmId={}, status={}",
            campaignId, nmId, status);
        return Optional.empty();
      }

      log.warn("WB bid recommendations fetch failed: campaignId={}, "
              + "nmId={}, status={}, body={}",
          campaignId, nmId, status, e.getResponseBodyAsString());
      throw e;

    } catch (Exception e) {
      log.error("WB bid recommendations error: campaignId={}, nmId={}, "
          + "error={}", campaignId, nmId, e.getMessage(), e);
      throw e;
    }
  }
}
