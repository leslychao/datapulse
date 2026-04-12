package io.datapulse.bidding.adapter.wb;

import java.util.Map;

import org.springframework.stereotype.Component;

import io.datapulse.bidding.adapter.wb.dto.WbCampaignParamsResponse;
import io.datapulse.bidding.adapter.wb.dto.WbCampaignParamsResponse.NmEntry;
import io.datapulse.bidding.domain.BidReadAdapter;
import io.datapulse.bidding.domain.BidReadResult;
import io.datapulse.integration.domain.CredentialKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads current bid information from WB /advert/v0/bids/recommendations.
 * Returns competitive/leaders bids in kopecks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WbBidReadAdapterImpl implements BidReadAdapter {

  private final WbBidReadAdapter readAdapter;

  @Override
  public BidReadResult readCurrentBid(
      String campaignExternalId,
      String marketplaceSku,
      long connectionId,
      Map<String, String> credentials) {

    String apiToken = credentials.get(CredentialKeys.WB_API_TOKEN);
    if (apiToken == null) {
      log.warn("WB bid read: missing apiToken for connection={}", connectionId);
      return BidReadResult.empty();
    }

    if (campaignExternalId == null || marketplaceSku == null) {
      log.debug("WB bid read: no campaign or sku for connection={}",
          connectionId);
      return BidReadResult.empty();
    }

    long campaignId;
    long nmId;
    try {
      campaignId = Long.parseLong(campaignExternalId);
      nmId = Long.parseLong(marketplaceSku);
    } catch (NumberFormatException e) {
      log.debug("WB bid read: invalid campaignId={} or nmId={}",
          campaignExternalId, marketplaceSku);
      return BidReadResult.empty();
    }

    try {
      return readAdapter.getRecommendedBids(
              campaignId, nmId, connectionId, apiToken)
          .map(response -> extractBidInfo(response, nmId))
          .orElse(BidReadResult.empty());
    } catch (Exception e) {
      log.warn("WB bid read failed: campaignId={}, nmId={}, error={}",
          campaignId, nmId, e.getMessage());
      return BidReadResult.empty();
    }
  }

  @Override
  public String marketplaceType() {
    return "WB";
  }

  private BidReadResult extractBidInfo(
      WbCampaignParamsResponse response, long nmId) {
    if (response.nms() == null || response.nms().isEmpty()) {
      return BidReadResult.empty();
    }
    return response.nms().stream()
        .filter(nm -> nm.nmId() == nmId)
        .findFirst()
        .map(this::toBidReadResult)
        .orElse(BidReadResult.empty());
  }

  private BidReadResult toBidReadResult(NmEntry nm) {
    if (nm.bids() == null) {
      return BidReadResult.empty();
    }
    return BidReadResult.of(
        null,
        null,
        nm.bids().competitiveBid(),
        nm.bids().leadersBid());
  }
}
