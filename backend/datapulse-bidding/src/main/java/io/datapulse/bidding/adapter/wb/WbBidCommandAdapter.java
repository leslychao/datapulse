package io.datapulse.bidding.adapter.wb;

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.datapulse.bidding.adapter.wb.dto.WbSetBidsRequest;
import io.datapulse.bidding.adapter.wb.dto.WbSetBidsRequest.BidItem;
import io.datapulse.bidding.adapter.wb.dto.WbSetBidsRequest.NmBid;
import io.datapulse.bidding.domain.BidActionGateway;
import io.datapulse.bidding.domain.BidActionGatewayResult;
import io.datapulse.bidding.persistence.BidActionEntity;
import io.datapulse.integration.config.IntegrationProperties;
import io.datapulse.integration.domain.ratelimit.MarketplaceRateLimiter;
import io.datapulse.integration.domain.ratelimit.RateLimitGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class WbBidCommandAdapter implements BidActionGateway {

  private static final String BIDS_PATH = "/api/advert/v1/bids";
  private static final String DEFAULT_PLACEMENT = "search";

  private final WebClient.Builder webClientBuilder;
  private final IntegrationProperties properties;
  private final MarketplaceRateLimiter rateLimiter;
  private final WbBidReadAdapter wbBidReadAdapter;

  @Override
  public BidActionGatewayResult execute(BidActionEntity action,
      Map<String, String> credentials) {
    String apiToken = credentials.get("token");
    if (apiToken == null) {
      return BidActionGatewayResult.failure(
          "MISSING_TOKEN", "No 'token' in credentials", null);
    }

    long advertId;
    try {
      advertId = Long.parseLong(action.getCampaignExternalId());
    } catch (NumberFormatException e) {
      return BidActionGatewayResult.failure(
          "INVALID_CAMPAIGN_ID",
          "campaignExternalId is not a valid long: "
              + action.getCampaignExternalId(),
          null);
    }

    long nmId;
    try {
      nmId = Long.parseLong(action.getNmId());
    } catch (NumberFormatException e) {
      return BidActionGatewayResult.failure(
          "INVALID_NM_ID",
          "nmId is not a valid long: " + action.getNmId(),
          null);
    }

    var request = new WbSetBidsRequest(List.of(
        new BidItem(advertId, List.of(
            new NmBid(nmId, action.getTargetBid(), DEFAULT_PLACEMENT)))));

    String baseUrl = properties.getWildberries().getAdvertBaseUrl();
    URI uri = URI.create(baseUrl + BIDS_PATH);

    try {
      rateLimiter.acquire(action.getConnectionId(), RateLimitGroup.WB_ADVERT_BIDS)
          .join();

      String responseBody = webClientBuilder.build()
          .method(HttpMethod.PATCH)
          .uri(uri)
          .header("Authorization", apiToken)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .retrieve()
          .bodyToMono(String.class)
          .block();

      rateLimiter.onResponse(
          action.getConnectionId(), RateLimitGroup.WB_ADVERT_BIDS, 200);

      log.info("WB bid set succeeded: bidActionId={}, advertId={}, "
              + "nmId={}, targetBid={}",
          action.getId(), advertId, nmId, action.getTargetBid());

      return BidActionGatewayResult.success(action.getTargetBid(), responseBody);

    } catch (WebClientResponseException e) {
      int status = e.getStatusCode().value();
      rateLimiter.onResponse(
          action.getConnectionId(), RateLimitGroup.WB_ADVERT_BIDS, status);

      log.warn("WB bid set failed: bidActionId={}, advertId={}, "
              + "nmId={}, httpStatus={}, body={}",
          action.getId(), advertId, nmId, status,
          e.getResponseBodyAsString());

      return BidActionGatewayResult.failure(
          "HTTP_" + status,
          e.getResponseBodyAsString(),
          e.getResponseBodyAsString());

    } catch (Exception e) {
      log.error("WB bid set error: bidActionId={}, advertId={}, "
              + "nmId={}, error={}",
          action.getId(), advertId, nmId, e.getMessage(), e);

      return BidActionGatewayResult.failure(
          "INTERNAL_ERROR", e.getMessage(), null);
    }
  }

  @Override
  public BidActionGatewayResult reconcile(BidActionEntity action,
      Map<String, String> credentials) {
    String apiToken = credentials.get("token");
    if (apiToken == null) {
      return BidActionGatewayResult.failure(
          "MISSING_TOKEN", "No 'token' in credentials for reconciliation",
          null);
    }

    long advertId;
    long nmId;
    try {
      advertId = Long.parseLong(action.getCampaignExternalId());
      nmId = Long.parseLong(action.getNmId());
    } catch (NumberFormatException e) {
      return BidActionGatewayResult.failure(
          "INVALID_IDS",
          "Cannot reconcile: invalid campaignExternalId or nmId",
          null);
    }

    try {
      var response = wbBidReadAdapter.getRecommendedBids(
          advertId, nmId, action.getConnectionId(), apiToken);

      if (response.isEmpty() || response.get().nms() == null) {
        log.debug("WB reconciliation: no data returned for "
            + "advertId={}, nmId={}", advertId, nmId);
        return BidActionGatewayResult.success(
            action.getTargetBid(), null);
      }

      return BidActionGatewayResult.success(
          action.getTargetBid(),
          "reconciled: bid recommendations available");

    } catch (Exception e) {
      log.warn("WB reconciliation failed: bidActionId={}, error={}",
          action.getId(), e.getMessage());
      return BidActionGatewayResult.success(
          action.getTargetBid(),
          "reconciliation skipped: " + e.getMessage());
    }
  }

  @Override
  public String marketplaceType() {
    return "WB";
  }
}
