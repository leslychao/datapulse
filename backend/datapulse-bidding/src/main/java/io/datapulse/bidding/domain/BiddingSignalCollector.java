package io.datapulse.bidding.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import io.datapulse.bidding.config.BiddingProperties;
import io.datapulse.bidding.persistence.AdvertisingMetricsRow;
import io.datapulse.bidding.persistence.BidDecisionRow;
import io.datapulse.bidding.persistence.BiddingClickHouseReadRepository;
import io.datapulse.bidding.persistence.BiddingDataReadRepository;
import io.datapulse.bidding.persistence.CampaignInfoRow;
import io.datapulse.bidding.persistence.MarginMetricsRow;
import io.datapulse.bidding.persistence.StockMetricsRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BiddingSignalCollector {

  private final BiddingDataReadRepository pgRepo;
  private final BiddingClickHouseReadRepository chRepo;
  private final BiddingProperties properties;

  /**
   * Collects all available signals for a single marketplace offer.
   * Missing data is represented as null in the returned {@link BiddingSignalSet} —
   * the strategy and guards decide how to handle absent signals.
   *
   * @param workspaceId      workspace scope
   * @param marketplaceOfferId  PG marketplace_offer.id (used for PG lookups)
   * @param marketplaceSku   external SKU (e.g. nmId) — used for ClickHouse lookups
   * @param lookbackDays     number of days for aggregation window
   */
  public BiddingSignalSet collect(
      long workspaceId,
      long marketplaceOfferId,
      String marketplaceSku,
      int lookbackDays) {

    CampaignInfoRow campaign = pgRepo.findCampaignInfo(marketplaceOfferId)
        .orElse(null);

    AdvertisingMetricsRow adMetrics = chRepo.findAdvertisingMetrics(
        workspaceId, marketplaceSku, lookbackDays).orElse(null);

    MarginMetricsRow marginMetrics = chRepo.findMarginMetrics(
        workspaceId, marketplaceSku, lookbackDays).orElse(null);

    StockMetricsRow stockMetrics = chRepo.findStockMetrics(
        workspaceId, marketplaceSku).orElse(null);

    BidDecisionRow lastDecision = pgRepo.findLastDecision(
        workspaceId, marketplaceOfferId).orElse(null);

    BidDecisionType previousDecisionType = resolvePreviousDecisionType(lastDecision);
    Integer daysSinceLastChange = resolveDaysSinceLastChange(lastDecision);

    return new BiddingSignalSet(
        null,
        adMetrics != null ? adMetrics.drrPct() : null,
        adMetrics != null ? adMetrics.cpoPct() : null,
        adMetrics != null ? adMetrics.roas() : null,
        adMetrics != null ? adMetrics.impressions() : 0,
        adMetrics != null ? adMetrics.clicks() : 0,
        adMetrics != null ? adMetrics.adOrders() : 0,
        adMetrics != null ? adMetrics.totalSpend() : null,
        marginMetrics != null ? marginMetrics.marginPct() : null,
        stockMetrics != null ? stockMetrics.stockDays() : null,
        null,
        null,
        null,
        previousDecisionType,
        daysSinceLastChange,
        campaign != null ? campaign.status() : null
    );
  }

  /**
   * Checks whether the signal set contains minimum data for strategy evaluation.
   * Currently only requires campaignStatus to be present — the rest is optional
   * and the strategy decides what to do with missing signals.
   */
  public boolean hasMinimumData(BiddingSignalSet signals) {
    return signals.campaignStatus() != null;
  }

  private BidDecisionType resolvePreviousDecisionType(BidDecisionRow row) {
    if (row == null || row.decisionType() == null) {
      return null;
    }
    try {
      return BidDecisionType.valueOf(row.decisionType());
    } catch (IllegalArgumentException e) {
      log.warn("Unknown previous decision type: value={}", row.decisionType());
      return null;
    }
  }

  private Integer resolveDaysSinceLastChange(BidDecisionRow row) {
    if (row == null || row.createdAt() == null) {
      return null;
    }
    long days = ChronoUnit.DAYS.between(row.createdAt(), Instant.now());
    return (int) days;
  }
}
