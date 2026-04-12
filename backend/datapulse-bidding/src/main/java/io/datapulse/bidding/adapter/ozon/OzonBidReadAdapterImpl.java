package io.datapulse.bidding.adapter.ozon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.datapulse.bidding.adapter.ozon.dto.OzonRecommendedBidsResponse;
import io.datapulse.bidding.domain.BidReadAdapter;
import io.datapulse.bidding.domain.BidReadResult;
import io.datapulse.integration.domain.CredentialKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads recommended bids from Ozon Performance API.
 * Converts rubles from API to kopecks for internal use.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OzonBidReadAdapterImpl implements BidReadAdapter {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final OzonBidReadAdapter readAdapter;
  private final OzonPerformanceAuthService authService;

  @Override
  public BidReadResult readCurrentBid(
      String campaignExternalId,
      String marketplaceSku,
      long connectionId,
      Map<String, String> credentials) {

    String clientId = credentials.get(CredentialKeys.OZON_PERFORMANCE_CLIENT_ID);
    String clientSecret = credentials.get(
        CredentialKeys.OZON_PERFORMANCE_CLIENT_SECRET);

    if (clientId == null || clientSecret == null) {
      log.warn("Ozon bid read: missing performance credentials "
          + "for connection={}", connectionId);
      return BidReadResult.empty();
    }

    if (marketplaceSku == null) {
      return BidReadResult.empty();
    }

    long skuId;
    try {
      skuId = Long.parseLong(marketplaceSku);
    } catch (NumberFormatException e) {
      log.debug("Ozon bid read: invalid skuId={}", marketplaceSku);
      return BidReadResult.empty();
    }

    String accessToken;
    try {
      accessToken = authService.getAccessToken(clientId, clientSecret);
    } catch (Exception e) {
      log.warn("Ozon bid read: OAuth2 token failed for connection={}, "
          + "error={}", connectionId, e.getMessage());
      return BidReadResult.empty();
    }

    try {
      return readAdapter.getRecommendedBids(
              List.of(skuId), accessToken, connectionId)
          .map(resp -> extractBidInfo(resp, skuId))
          .orElse(BidReadResult.empty());
    } catch (Exception e) {
      log.warn("Ozon bid read failed: sku={}, error={}",
          skuId, e.getMessage());
      return BidReadResult.empty();
    }
  }

  @Override
  public String marketplaceType() {
    return "OZON";
  }

  private BidReadResult extractBidInfo(
      OzonRecommendedBidsResponse response, long skuId) {
    if (response.recommendations() == null
        || response.recommendations().isEmpty()) {
      return BidReadResult.empty();
    }
    return response.recommendations().stream()
        .filter(r -> r.sku() == skuId)
        .findFirst()
        .map(r -> {
          Integer minBidKopecks = rublesToKopecks(r.recommendedBid());
          return BidReadResult.of(null, minBidKopecks, null, null);
        })
        .orElse(BidReadResult.empty());
  }

  private Integer rublesToKopecks(BigDecimal rubles) {
    if (rubles == null) {
      return null;
    }
    return rubles.multiply(HUNDRED)
        .setScale(0, RoundingMode.HALF_UP)
        .intValue();
  }
}
