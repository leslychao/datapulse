package io.datapulse.bidding.domain.guard;

import java.math.BigDecimal;

import io.datapulse.bidding.domain.BiddingSignalSet;

final class TestSignals {

  private TestSignals() {}

  static BiddingSignalSet defaults() {
    return new BiddingSignalSet(
        1000, new BigDecimal("10.0"), null, new BigDecimal("3.0"),
        100, 10, 5, BigDecimal.TEN,
        new BigDecimal("20.0"), 30, null, null, 50,
        null, null, "9");
  }

  static BiddingSignalSet withCampaignStatus(String status) {
    return new BiddingSignalSet(
        1000, new BigDecimal("10.0"), null, new BigDecimal("3.0"),
        100, 10, 5, BigDecimal.TEN,
        new BigDecimal("20.0"), 30, null, null, 50,
        null, null, status);
  }

  static BiddingSignalSet withNoActivity() {
    return new BiddingSignalSet(
        1000, new BigDecimal("10.0"), null, new BigDecimal("3.0"),
        0, 0, 0, BigDecimal.ZERO,
        new BigDecimal("20.0"), 30, null, null, 50,
        null, null, "9");
  }

  static BiddingSignalSet withActivity(long impressions, long clicks, long adOrders) {
    return new BiddingSignalSet(
        1000, new BigDecimal("10.0"), null, new BigDecimal("3.0"),
        impressions, clicks, adOrders, BigDecimal.TEN,
        new BigDecimal("20.0"), 30, null, null, 50,
        null, null, "9");
  }

  static BiddingSignalSet withDaysSinceLastChange(Integer days) {
    return new BiddingSignalSet(
        1000, new BigDecimal("10.0"), null, new BigDecimal("3.0"),
        100, 10, 5, BigDecimal.TEN,
        new BigDecimal("20.0"), 30, null, null, 50,
        null, days, "9");
  }

  static BiddingSignalSet withStockDays(Integer stockDays) {
    return new BiddingSignalSet(
        1000, new BigDecimal("10.0"), null, new BigDecimal("3.0"),
        100, 10, 5, BigDecimal.TEN,
        new BigDecimal("20.0"), stockDays, null, null, 50,
        null, null, "9");
  }

  static BiddingSignalSet withMarginPct(BigDecimal marginPct) {
    return new BiddingSignalSet(
        1000, new BigDecimal("10.0"), null, new BigDecimal("3.0"),
        100, 10, 5, BigDecimal.TEN,
        marginPct, 30, null, null, 50,
        null, null, "9");
  }

  static BiddingSignalSet withDrrPct(BigDecimal drrPct) {
    return new BiddingSignalSet(
        1000, drrPct, null, new BigDecimal("3.0"),
        100, 10, 5, BigDecimal.TEN,
        new BigDecimal("20.0"), 30, null, null, 50,
        null, null, "9");
  }
}
