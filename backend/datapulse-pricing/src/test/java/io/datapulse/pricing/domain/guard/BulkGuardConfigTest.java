package io.datapulse.pricing.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.datapulse.pricing.domain.GuardConfig;
import io.datapulse.pricing.domain.GuardEvaluationRecord;
import io.datapulse.pricing.domain.PricingSignalSet;
import io.datapulse.pricing.domain.guard.PricingGuardChain.GuardChainResult;

/**
 * Tests the guard pipeline with BULK_GUARD_CONFIG — the configuration used
 * for manual bulk pricing operations (MANUAL_BULK trigger).
 *
 * Uses real guard classes (no mocks) wired through PricingGuardChain,
 * verifying that frequency/volatility/stock-out are skipped while
 * promo, manual_lock, stale_data, and margin guards remain active.
 */
@DisplayName("Guard pipeline with BULK_GUARD_CONFIG")
class BulkGuardConfigTest {

  /**
   * Mirrors BulkManualPricingService.BULK_GUARD_CONFIG:
   * margin=true, frequency=false, volatility=false, promo=true, stockOut=false,
   * ad_cost=disabled, competitor guards=disabled.
   */
  private static final GuardConfig BULK_GUARD_CONFIG = new GuardConfig(
      true, false, null, false, null, null, true, false, null, null, null
  );

  private PricingGuardChain guardChain;

  @BeforeEach
  void setUp() {
    List<PricingGuard> guards = List.of(
        new ManualLockGuard(),
        new StaleDataGuard(),
        new CompetitorFreshnessGuard(),
        new CompetitorTrustGuard(),
        new StockOutGuard(),
        new PromoGuard(),
        new MarginGuard(),
        new FrequencyGuard(),
        new VolatilityGuard(),
        new AdCostGuard()
    );
    guardChain = new PricingGuardChain(guards);
  }

  @Nested
  @DisplayName("Disabled guards for MANUAL_BULK")
  class DisabledGuards {

    @Test
    @DisplayName("should_passFrequency_when_recentPriceChange")
    void should_passFrequency_when_recentPriceChange() {
      PricingSignalSet signals = bulkSignals()
          .withLastPriceChangeAt(OffsetDateTime.now().minusMinutes(10))
          .build();

      GuardChainResult result = guardChain.evaluate(
          signals, new BigDecimal("1200"), BULK_GUARD_CONFIG);

      assertThat(result.allPassed()).isTrue();
      assertGuardPassed(result, "frequency_guard");
    }

    @Test
    @DisplayName("should_passVolatility_when_highReversals")
    void should_passVolatility_when_highReversals() {
      PricingSignalSet signals = bulkSignals()
          .withPriceReversalsInPeriod(10)
          .build();

      GuardChainResult result = guardChain.evaluate(
          signals, new BigDecimal("1200"), BULK_GUARD_CONFIG);

      assertThat(result.allPassed()).isTrue();
      assertGuardPassed(result, "volatility_guard");
    }

    @Test
    @DisplayName("should_passStockOut_when_zeroStock")
    void should_passStockOut_when_zeroStock() {
      PricingSignalSet signals = bulkSignals()
          .withAvailableStock(0)
          .build();

      GuardChainResult result = guardChain.evaluate(
          signals, new BigDecimal("1200"), BULK_GUARD_CONFIG);

      assertThat(result.allPassed()).isTrue();
      assertGuardPassed(result, "stock_out_guard");
    }
  }

  @Nested
  @DisplayName("Active guards for MANUAL_BULK")
  class ActiveGuards {

    @Test
    @DisplayName("should_blockPromo_when_offerInPromo")
    void should_blockPromo_when_offerInPromo() {
      PricingSignalSet signals = bulkSignals()
          .withPromoActive(true)
          .build();

      GuardChainResult result = guardChain.evaluate(
          signals, new BigDecimal("1200"), BULK_GUARD_CONFIG);

      assertThat(result.allPassed()).isFalse();
      assertThat(result.blockingGuard()).isNotNull();
      assertThat(result.blockingGuard().guardName()).isEqualTo("promo_guard");
    }

    @Test
    @DisplayName("should_blockManualLock_when_offerLocked")
    void should_blockManualLock_when_offerLocked() {
      PricingSignalSet signals = bulkSignals()
          .withManualLockActive(true)
          .build();

      GuardChainResult result = guardChain.evaluate(
          signals, new BigDecimal("1200"), BULK_GUARD_CONFIG);

      assertThat(result.allPassed()).isFalse();
      assertThat(result.blockingGuard()).isNotNull();
      assertThat(result.blockingGuard().guardName()).isEqualTo("manual_lock_guard");
    }

    @Test
    @DisplayName("should_blockStaleData_when_dataTooOld")
    void should_blockStaleData_when_dataTooOld() {
      PricingSignalSet signals = bulkSignals()
          .withDataFreshnessAt(OffsetDateTime.now().minusHours(48))
          .build();

      GuardChainResult result = guardChain.evaluate(
          signals, new BigDecimal("1200"), BULK_GUARD_CONFIG);

      assertThat(result.allPassed()).isFalse();
      assertThat(result.blockingGuard()).isNotNull();
      assertThat(result.blockingGuard().guardName()).isEqualTo("stale_data_guard");
    }

    @Test
    @DisplayName("should_blockStaleData_when_dataFreshnessNull")
    void should_blockStaleData_when_dataFreshnessNull() {
      PricingSignalSet signals = bulkSignals()
          .withDataFreshnessAt(null)
          .build();

      GuardChainResult result = guardChain.evaluate(
          signals, new BigDecimal("1200"), BULK_GUARD_CONFIG);

      assertThat(result.allPassed()).isFalse();
      assertThat(result.blockingGuard()).isNotNull();
      assertThat(result.blockingGuard().guardName()).isEqualTo("stale_data_guard");
    }

    @Test
    @DisplayName("should_passMargin_when_marginNonNegative")
    void should_passMargin_when_marginNonNegative() {
      PricingSignalSet signals = bulkSignals()
          .withCogs(new BigDecimal("500"))
          .withCurrentPrice(new BigDecimal("1000"))
          .build();

      GuardChainResult result = guardChain.evaluate(
          signals, new BigDecimal("1200"), BULK_GUARD_CONFIG);

      assertThat(result.allPassed()).isTrue();
      assertGuardPassed(result, "margin_guard");
    }

    @Test
    @DisplayName("should_blockMargin_when_marginNegative")
    void should_blockMargin_when_marginNegative() {
      PricingSignalSet signals = bulkSignals()
          .withCogs(new BigDecimal("1500"))
          .withCurrentPrice(new BigDecimal("1000"))
          .build();

      GuardChainResult result = guardChain.evaluate(
          signals, new BigDecimal("100"), BULK_GUARD_CONFIG);

      assertThat(result.allPassed()).isFalse();
      assertThat(result.blockingGuard()).isNotNull();
      assertThat(result.blockingGuard().guardName()).isEqualTo("margin_guard");
    }
  }

  @Nested
  @DisplayName("Combined scenarios")
  class CombinedScenarios {

    @Test
    @DisplayName("should_passAll_when_normalBulkScenario")
    void should_passAll_when_normalBulkScenario() {
      PricingSignalSet signals = bulkSignals()
          .withLastPriceChangeAt(OffsetDateTime.now().minusMinutes(5))
          .withPriceReversalsInPeriod(5)
          .withAvailableStock(0)
          .build();

      GuardChainResult result = guardChain.evaluate(
          signals, new BigDecimal("1200"), BULK_GUARD_CONFIG);

      assertThat(result.allPassed()).isTrue();
      assertThat(result.evaluations()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("should_shortCircuit_when_manualLockBeforePromo")
    void should_shortCircuit_when_manualLockBeforePromo() {
      PricingSignalSet signals = bulkSignals()
          .withManualLockActive(true)
          .withPromoActive(true)
          .build();

      GuardChainResult result = guardChain.evaluate(
          signals, new BigDecimal("1200"), BULK_GUARD_CONFIG);

      assertThat(result.allPassed()).isFalse();
      assertThat(result.blockingGuard().guardName()).isEqualTo("manual_lock_guard");
      assertThat(result.evaluations()).hasSize(1);
    }
  }

  private void assertGuardPassed(GuardChainResult result, String guardName) {
    assertThat(result.evaluations())
        .filteredOn(e -> e.name().equals(guardName))
        .allMatch(GuardEvaluationRecord::passed,
            "expected guard '%s' to pass".formatted(guardName));
  }

  private static BulkSignalBuilder bulkSignals() {
    return new BulkSignalBuilder();
  }

  /**
   * Builder for PricingSignalSet in bulk-context tests.
   * Defaults match a "healthy" bulk scenario: fresh data, no locks, no promo.
   */
  private static class BulkSignalBuilder {

    private BigDecimal currentPrice = new BigDecimal("1000");
    private BigDecimal cogs = new BigDecimal("500");
    private String productStatus = "ACTIVE";
    private Integer availableStock = null;
    private boolean manualLockActive = false;
    private boolean promoActive = false;
    private OffsetDateTime lastPriceChangeAt = null;
    private Integer priceReversalsInPeriod = null;
    private OffsetDateTime dataFreshnessAt = OffsetDateTime.now().minusHours(1);

    BulkSignalBuilder withCurrentPrice(BigDecimal v) { currentPrice = v; return this; }
    BulkSignalBuilder withCogs(BigDecimal v) { cogs = v; return this; }
    BulkSignalBuilder withAvailableStock(Integer v) { availableStock = v; return this; }
    BulkSignalBuilder withManualLockActive(boolean v) { manualLockActive = v; return this; }
    BulkSignalBuilder withPromoActive(boolean v) { promoActive = v; return this; }
    BulkSignalBuilder withLastPriceChangeAt(OffsetDateTime v) { lastPriceChangeAt = v; return this; }
    BulkSignalBuilder withPriceReversalsInPeriod(Integer v) { priceReversalsInPeriod = v; return this; }
    BulkSignalBuilder withDataFreshnessAt(OffsetDateTime v) { dataFreshnessAt = v; return this; }

    PricingSignalSet build() {
      return new PricingSignalSet(
          currentPrice, cogs, productStatus, availableStock,
          manualLockActive, promoActive,
          null, null, null, null,
          lastPriceChangeAt, priceReversalsInPeriod, dataFreshnessAt,
          null, null, null, null,
          null, null, null, null, null);
    }
  }
}
