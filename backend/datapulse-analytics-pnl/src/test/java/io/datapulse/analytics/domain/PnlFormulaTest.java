package io.datapulse.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure computation tests for P&L formula that the materializers execute in SQL.
 * Validates the math independently of ClickHouse.
 *
 * Formula:
 *   marketplace_pnl = revenue + commission + acquiring + logistics + storage
 *                   + penalties + marketing + acceptance + other + compensation + refund
 *
 *   gross_cogs = quantity * cost_price
 *   refund_ratio = |refund| / revenue
 *   net_cogs = gross_cogs * max(0, 1 - refund_ratio)
 *   full_pnl = marketplace_pnl - advertising - net_cogs
 */
@DisplayName("P&L Formula — pure calculation tests")
class PnlFormulaTest {

  private static final int SCALE = 2;
  private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

  private BigDecimal marketplacePnl(
      BigDecimal revenue, BigDecimal commission, BigDecimal acquiring,
      BigDecimal logistics, BigDecimal storage, BigDecimal penalties,
      BigDecimal marketing, BigDecimal acceptance, BigDecimal other,
      BigDecimal compensation, BigDecimal refund) {
    return revenue.add(commission).add(acquiring).add(logistics)
        .add(storage).add(penalties).add(marketing).add(acceptance)
        .add(other).add(compensation).add(refund);
  }

  private BigDecimal grossCogs(int quantity, BigDecimal costPrice) {
    if (costPrice == null) return null;
    return BigDecimal.valueOf(quantity).multiply(costPrice)
        .setScale(SCALE, ROUNDING);
  }

  private BigDecimal refundRatio(BigDecimal refund, BigDecimal revenue) {
    if (revenue.compareTo(BigDecimal.ZERO) == 0) return null;
    return refund.abs().divide(revenue, 4, ROUNDING);
  }

  private BigDecimal netCogs(BigDecimal grossCogs, BigDecimal refundRatio) {
    if (grossCogs == null) return null;
    BigDecimal factor = BigDecimal.ONE.subtract(
        refundRatio != null ? refundRatio : BigDecimal.ZERO);
    if (factor.compareTo(BigDecimal.ZERO) < 0) {
      factor = BigDecimal.ZERO;
    }
    return grossCogs.multiply(factor).setScale(SCALE, ROUNDING);
  }

  private BigDecimal fullPnl(BigDecimal marketplacePnl, BigDecimal advertising,
      BigDecimal netCogs) {
    if (netCogs == null) return null;
    return marketplacePnl.subtract(advertising).subtract(netCogs)
        .setScale(SCALE, ROUNDING);
  }

  @Nested
  @DisplayName("marketplace_pnl calculation")
  class MarketplacePnl {

    @Test
    @DisplayName("should equal revenue + all cost components (costs are negative)")
    void should_sumAll11Components_when_calculated() {
      BigDecimal result = marketplacePnl(
          new BigDecimal("100000.00"),
          new BigDecimal("-15000.00"),
          new BigDecimal("-3000.00"),
          new BigDecimal("-8000.00"),
          new BigDecimal("-2000.00"),
          new BigDecimal("-500.00"),
          new BigDecimal("-1000.00"),
          new BigDecimal("-400.00"),
          new BigDecimal("-600.00"),
          new BigDecimal("500.00"),
          new BigDecimal("-3000.00"));

      assertThat(result).isEqualByComparingTo("67000.00");
    }

    @Test
    @DisplayName("should be zero when all components are zero")
    void should_returnZero_when_allComponentsZero() {
      BigDecimal result = marketplacePnl(
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO);

      assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("should be negative when costs exceed revenue (loss)")
    void should_beNegative_when_costsExceedRevenue() {
      BigDecimal result = marketplacePnl(
          new BigDecimal("10000.00"),
          new BigDecimal("-8000.00"),
          new BigDecimal("-1000.00"),
          new BigDecimal("-5000.00"),
          new BigDecimal("-500.00"),
          new BigDecimal("-200.00"),
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          BigDecimal.ZERO, BigDecimal.ZERO);

      assertThat(result).isNegative();
      assertThat(result).isEqualByComparingTo("-4700.00");
    }
  }

  @Nested
  @DisplayName("COGS calculation")
  class Cogs {

    @Test
    @DisplayName("should compute gross_cogs = quantity * cost_price")
    void should_multiplyQuantityByCost_when_calculated() {
      BigDecimal result = grossCogs(10, new BigDecimal("250.00"));

      assertThat(result).isEqualByComparingTo("2500.00");
    }

    @Test
    @DisplayName("should return null when cost_price is null (NO_COST_PROFILE)")
    void should_returnNull_when_noCostProfile() {
      BigDecimal result = grossCogs(10, null);

      assertThat(result).isNull();
    }

    @Test
    @DisplayName("should be zero when quantity is zero")
    void should_returnZero_when_quantityIsZero() {
      BigDecimal result = grossCogs(0, new BigDecimal("250.00"));

      assertThat(result).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("should preserve precision with HALF_UP rounding")
    void should_preservePrecision_when_fractionalCost() {
      BigDecimal result = grossCogs(3, new BigDecimal("33.33"));

      assertThat(result).isEqualByComparingTo("99.99");
      assertThat(result.scale()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("refund_ratio calculation")
  class RefundRatio {

    @Test
    @DisplayName("should compute |refund| / revenue")
    void should_computeAbsRefundOverRevenue() {
      BigDecimal result = refundRatio(
          new BigDecimal("-3000.00"), new BigDecimal("100000.00"));

      assertThat(result).isEqualByComparingTo("0.0300");
    }

    @Test
    @DisplayName("should return null when revenue is zero (division guard)")
    void should_returnNull_when_revenueIsZero() {
      BigDecimal result = refundRatio(
          new BigDecimal("-500.00"), BigDecimal.ZERO);

      assertThat(result).isNull();
    }

    @Test
    @DisplayName("should be zero when no refunds")
    void should_returnZero_when_noRefunds() {
      BigDecimal result = refundRatio(
          BigDecimal.ZERO, new BigDecimal("50000.00"));

      assertThat(result).isEqualByComparingTo("0.0000");
    }
  }

  @Nested
  @DisplayName("net_cogs (revenue-ratio netting)")
  class NetCogs {

    @Test
    @DisplayName("should compute gross_cogs * (1 - refund_ratio)")
    void should_applyRefundNetting_when_calculated() {
      BigDecimal gross = new BigDecimal("2500.00");
      BigDecimal ratio = new BigDecimal("0.0300");

      BigDecimal result = netCogs(gross, ratio);

      assertThat(result).isEqualByComparingTo("2425.00");
    }

    @Test
    @DisplayName("should return null when gross_cogs is null")
    void should_returnNull_when_grossCogsNull() {
      BigDecimal result = netCogs(null, new BigDecimal("0.05"));

      assertThat(result).isNull();
    }

    @Test
    @DisplayName("should equal gross_cogs when refund_ratio is null")
    void should_equalGross_when_refundRatioNull() {
      BigDecimal gross = new BigDecimal("2500.00");

      BigDecimal result = netCogs(gross, null);

      assertThat(result).isEqualByComparingTo("2500.00");
    }

    @Test
    @DisplayName("should floor to zero when refund_ratio > 1.0")
    void should_floorToZero_when_refundRatioExceedsOne() {
      BigDecimal gross = new BigDecimal("1000.00");
      BigDecimal ratio = new BigDecimal("1.5000");

      BigDecimal result = netCogs(gross, ratio);

      assertThat(result).isEqualByComparingTo("0.00");
    }
  }

  @Nested
  @DisplayName("full_pnl calculation")
  class FullPnl {

    @Test
    @DisplayName("should compute marketplace_pnl - advertising - net_cogs")
    void should_subtractAdvertisingAndCogs_when_calculated() {
      BigDecimal mp = new BigDecimal("67000.00");
      BigDecimal adv = new BigDecimal("5000.00");
      BigDecimal nc = new BigDecimal("18000.00");

      BigDecimal result = fullPnl(mp, adv, nc);

      assertThat(result).isEqualByComparingTo("44000.00");
    }

    @Test
    @DisplayName("should return null when net_cogs is null")
    void should_returnNull_when_netCogsIsNull() {
      BigDecimal result = fullPnl(
          new BigDecimal("67000.00"), BigDecimal.ZERO, null);

      assertThat(result).isNull();
    }

    @Test
    @DisplayName("should be zero when all equal zero")
    void should_returnZero_when_allZero() {
      BigDecimal result = fullPnl(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

      assertThat(result).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("should be negative when costs exceed marketplace profit")
    void should_beNegative_when_costsExceedProfit() {
      BigDecimal mp = new BigDecimal("10000.00");
      BigDecimal adv = new BigDecimal("5000.00");
      BigDecimal nc = new BigDecimal("8000.00");

      BigDecimal result = fullPnl(mp, adv, nc);

      assertThat(result).isNegative();
      assertThat(result).isEqualByComparingTo("-3000.00");
    }

    @Test
    @DisplayName("should preserve 2 decimal places with HALF_UP")
    void should_preserveScale_when_fractionalResult() {
      BigDecimal mp = new BigDecimal("67000.33");
      BigDecimal adv = new BigDecimal("5000.11");
      BigDecimal nc = new BigDecimal("18000.55");

      BigDecimal result = fullPnl(mp, adv, nc);

      assertThat(result).isEqualByComparingTo("43999.67");
      assertThat(result.scale()).isEqualTo(2);
    }
  }
}
