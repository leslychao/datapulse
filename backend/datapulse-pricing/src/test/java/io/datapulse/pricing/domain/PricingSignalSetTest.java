package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PricingSignalSetTest {

  @Nested
  @DisplayName("construction — all fields")
  class AllFields {

    @Test
    @DisplayName("stores all signal values")
    void should_storeAllValues_when_allFieldsProvided() {
      OffsetDateTime now = OffsetDateTime.now();
      PricingSignalSet signals = new PricingSignalSet(
          new BigDecimal("1000"),
          new BigDecimal("400"),
          "ACTIVE",
          50,
          true,
          false,
          new BigDecimal("0.12"),
          new BigDecimal("80"),
          new BigDecimal("0.05"),
          new BigDecimal("0.03"),
          now.minusHours(5),
          2,
          now,
          new BigDecimal("50"),
          new BigDecimal("3.5"),
          new BigDecimal("2.1"),
          new BigDecimal("45"),
          null, null,
          null, null, null);

      assertThat(signals.currentPrice()).isEqualByComparingTo(new BigDecimal("1000"));
      assertThat(signals.cogs()).isEqualByComparingTo(new BigDecimal("400"));
      assertThat(signals.productStatus()).isEqualTo("ACTIVE");
      assertThat(signals.availableStock()).isEqualTo(50);
      assertThat(signals.manualLockActive()).isTrue();
      assertThat(signals.promoActive()).isFalse();
      assertThat(signals.avgCommissionPct()).isEqualByComparingTo(new BigDecimal("0.12"));
      assertThat(signals.avgLogisticsPerUnit()).isEqualByComparingTo(new BigDecimal("80"));
      assertThat(signals.returnRatePct()).isEqualByComparingTo(new BigDecimal("0.05"));
      assertThat(signals.adCostRatio()).isEqualByComparingTo(new BigDecimal("0.03"));
      assertThat(signals.lastPriceChangeAt()).isBefore(now);
      assertThat(signals.priceReversalsInPeriod()).isEqualTo(2);
      assertThat(signals.dataFreshnessAt()).isEqualTo(now);
      assertThat(signals.marketplaceMinPrice()).isEqualByComparingTo(new BigDecimal("50"));
      assertThat(signals.salesVelocityShort()).isEqualByComparingTo(new BigDecimal("3.5"));
      assertThat(signals.salesVelocityLong()).isEqualByComparingTo(new BigDecimal("2.1"));
      assertThat(signals.daysOfCover()).isEqualByComparingTo(new BigDecimal("45"));
    }
  }

  @Nested
  @DisplayName("construction — partially null")
  class PartiallyNull {

    @Test
    @DisplayName("allows null for optional signal fields")
    void should_allowNull_when_signalsPartiallyMissing() {
      PricingSignalSet signals = new PricingSignalSet(
          null, null, null, null,
          false, false,
          null, null, null, null, null, null, null, null,
          null, null, null,
          null, null,
          null, null, null);

      assertThat(signals.currentPrice()).isNull();
      assertThat(signals.cogs()).isNull();
      assertThat(signals.productStatus()).isNull();
      assertThat(signals.availableStock()).isNull();
      assertThat(signals.manualLockActive()).isFalse();
      assertThat(signals.promoActive()).isFalse();
      assertThat(signals.avgCommissionPct()).isNull();
      assertThat(signals.avgLogisticsPerUnit()).isNull();
      assertThat(signals.returnRatePct()).isNull();
      assertThat(signals.adCostRatio()).isNull();
      assertThat(signals.lastPriceChangeAt()).isNull();
      assertThat(signals.priceReversalsInPeriod()).isNull();
      assertThat(signals.dataFreshnessAt()).isNull();
      assertThat(signals.marketplaceMinPrice()).isNull();
      assertThat(signals.salesVelocityShort()).isNull();
      assertThat(signals.salesVelocityLong()).isNull();
      assertThat(signals.daysOfCover()).isNull();
    }
  }

  @Nested
  @DisplayName("equality")
  class Equality {

    @Test
    @DisplayName("two signals with same values are equal (record semantics)")
    void should_beEqual_when_sameValues() {
      PricingSignalSet a = new PricingSignalSet(
          new BigDecimal("100"), null, null, null,
          false, false, null, null, null, null, null, null, null, null,
          null, null, null,
          null, null,
          null, null, null);
      PricingSignalSet b = new PricingSignalSet(
          new BigDecimal("100"), null, null, null,
          false, false, null, null, null, null, null, null, null, null,
          null, null, null,
          null, null,
          null, null, null);

      assertThat(a).isEqualTo(b);
    }
  }
}
