package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.datapulse.pricing.persistence.PricingClickHouseReadRepository;
import io.datapulse.pricing.persistence.PricingClickHouseReadRepository.CommissionResult;
import io.datapulse.pricing.persistence.PricingDataReadRepository;
import io.datapulse.pricing.persistence.PricingDataReadRepository.CurrentPriceRow;

@ExtendWith(MockitoExtension.class)
class PricingSignalCollectorTest {

  @Mock
  private PricingDataReadRepository dataReadRepository;

  @Mock
  private PricingClickHouseReadRepository clickHouseReadRepository;

  @InjectMocks
  private PricingSignalCollector collector;

  private static final long CONNECTION_ID = 1L;
  private static final int VOLATILITY_DAYS = 7;

  @Nested
  @DisplayName("collectBatch — empty input")
  class EmptyInput {

    @Test
    @DisplayName("returns empty map when offer list is empty")
    void should_returnEmptyMap_when_noOffers() {
      Map<Long, PricingSignalSet> result =
          collector.collectBatch(List.of(), CONNECTION_ID, VOLATILITY_DAYS);

      assertThat(result).isEmpty();
      verifyNoInteractions(dataReadRepository);
      verifyNoInteractions(clickHouseReadRepository);
    }
  }

  @Nested
  @DisplayName("collectBatch — with offers")
  class WithOffers {

    @Test
    @DisplayName("assembles signals for each offer from repository data")
    void should_assembleSignals_when_offersProvided() {
      List<Long> offerIds = List.of(100L, 200L);
      stubPgRepos(offerIds);

      when(dataReadRepository.findCurrentPrices(offerIds))
          .thenReturn(Map.of(
              100L, new CurrentPriceRow(new BigDecimal("999"), new BigDecimal("800")),
              200L, new CurrentPriceRow(new BigDecimal("1500"), null)));
      when(dataReadRepository.findCurrentCogs(offerIds))
          .thenReturn(Map.of(100L, new BigDecimal("400")));
      when(dataReadRepository.findTotalStock(offerIds))
          .thenReturn(Map.of(100L, 50, 200L, 0));
      when(dataReadRepository.findLockedOfferIds(offerIds))
          .thenReturn(List.of(200L));

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      assertThat(result).hasSize(2);

      PricingSignalSet signals100 = result.get(100L);
      assertThat(signals100.currentPrice()).isEqualByComparingTo(new BigDecimal("999"));
      assertThat(signals100.cogs()).isEqualByComparingTo(new BigDecimal("400"));
      assertThat(signals100.availableStock()).isEqualTo(50);
      assertThat(signals100.manualLockActive()).isFalse();
      assertThat(signals100.marketplaceMinPrice())
          .isEqualByComparingTo(new BigDecimal("800"));

      PricingSignalSet signals200 = result.get(200L);
      assertThat(signals200.currentPrice()).isEqualByComparingTo(new BigDecimal("1500"));
      assertThat(signals200.cogs()).isNull();
      assertThat(signals200.availableStock()).isEqualTo(0);
      assertThat(signals200.manualLockActive()).isTrue();
      assertThat(signals200.marketplaceMinPrice()).isNull();
    }

    @Test
    @DisplayName("handles null COGS and null stock gracefully")
    void should_handleNullSignals_when_dataNotAvailable() {
      List<Long> offerIds = List.of(300L);
      stubPgRepos(offerIds);
      when(dataReadRepository.findDataFreshness(CONNECTION_ID)).thenReturn(null);

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      PricingSignalSet signals = result.get(300L);
      assertThat(signals.currentPrice()).isNull();
      assertThat(signals.cogs()).isNull();
      assertThat(signals.productStatus()).isNull();
      assertThat(signals.availableStock()).isNull();
      assertThat(signals.manualLockActive()).isFalse();
      assertThat(signals.dataFreshnessAt()).isNull();
      assertThat(signals.marketplaceMinPrice()).isNull();
    }

    @Test
    @DisplayName("populates lastPriceChangeAt from latest change decisions")
    void should_populateLastChange_when_decisionExists() {
      List<Long> offerIds = List.of(100L);
      OffsetDateTime lastChange = OffsetDateTime.now().minusHours(12);

      stubPgRepos(offerIds);
      when(dataReadRepository.findLatestChangeDecisions(offerIds))
          .thenReturn(Map.of(100L, lastChange));

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      assertThat(result.get(100L).lastPriceChangeAt()).isEqualTo(lastChange);
    }

    @Test
    @DisplayName("populates reversals count from price reversals query")
    void should_populateReversals_when_reversalsExist() {
      List<Long> offerIds = List.of(100L);

      stubPgRepos(offerIds);
      when(dataReadRepository.findPriceReversals(eq(offerIds), any()))
          .thenReturn(Map.of(100L, 5));

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      assertThat(result.get(100L).priceReversalsInPeriod()).isEqualTo(5);
    }

    @Test
    @DisplayName("shares dataFreshness across all offers (one value per connection)")
    void should_shareDataFreshness_when_multipleOffers() {
      List<Long> offerIds = List.of(100L, 200L);
      OffsetDateTime freshness = OffsetDateTime.now().minusHours(2);

      stubPgRepos(offerIds);
      when(dataReadRepository.findDataFreshness(CONNECTION_ID)).thenReturn(freshness);

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      assertThat(result.get(100L).dataFreshnessAt()).isEqualTo(freshness);
      assertThat(result.get(200L).dataFreshnessAt()).isEqualTo(freshness);
    }

    @Test
    @DisplayName("calls findSellerSkuIds to build offerId→skuId mapping")
    void should_callFindSellerSkuIds_when_offersProvided() {
      List<Long> offerIds = List.of(100L);
      stubPgRepos(offerIds);

      collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      verify(dataReadRepository).findSellerSkuIds(offerIds);
    }

    @Test
    @DisplayName("populates productStatus from findOfferStatuses")
    void should_populateProductStatus_when_statusExists() {
      List<Long> offerIds = List.of(100L, 200L);
      stubPgRepos(offerIds);

      when(dataReadRepository.findOfferStatuses(offerIds))
          .thenReturn(Map.of(100L, "ACTIVE", 200L, "DISABLED"));

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      assertThat(result.get(100L).productStatus()).isEqualTo("ACTIVE");
      assertThat(result.get(200L).productStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("productStatus is null when offer not found in statuses map")
    void should_returnNullStatus_when_offerNotInStatusesMap() {
      List<Long> offerIds = List.of(100L);
      stubPgRepos(offerIds);

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      assertThat(result.get(100L).productStatus()).isNull();
    }

    @Test
    @DisplayName("populates marketplaceMinPrice from findCurrentPrices min_price")
    void should_populateMinPrice_when_minPriceExists() {
      List<Long> offerIds = List.of(100L);
      stubPgRepos(offerIds);

      when(dataReadRepository.findCurrentPrices(offerIds))
          .thenReturn(Map.of(
              100L, new CurrentPriceRow(new BigDecimal("1500"), new BigDecimal("1200"))));

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      PricingSignalSet signals = result.get(100L);
      assertThat(signals.currentPrice()).isEqualByComparingTo(new BigDecimal("1500"));
      assertThat(signals.marketplaceMinPrice())
          .isEqualByComparingTo(new BigDecimal("1200"));
    }

    @Test
    @DisplayName("marketplaceMinPrice is null when min_price absent in price row")
    void should_returnNullMinPrice_when_minPriceAbsent() {
      List<Long> offerIds = List.of(100L);
      stubPgRepos(offerIds);

      when(dataReadRepository.findCurrentPrices(offerIds))
          .thenReturn(Map.of(
              100L, new CurrentPriceRow(new BigDecimal("1500"), null)));

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      assertThat(result.get(100L).currentPrice())
          .isEqualByComparingTo(new BigDecimal("1500"));
      assertThat(result.get(100L).marketplaceMinPrice()).isNull();
    }

    @Test
    @DisplayName("both currentPrice and marketplaceMinPrice are null when no price row")
    void should_returnNullPriceAndMinPrice_when_noPriceRow() {
      List<Long> offerIds = List.of(100L);
      stubPgRepos(offerIds);

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      assertThat(result.get(100L).currentPrice()).isNull();
      assertThat(result.get(100L).marketplaceMinPrice()).isNull();
    }
  }

  @Nested
  @DisplayName("collectClickHouseSignals — commission per-SKU")
  class CommissionPerSku {

    @Test
    @DisplayName("returns empty signals when offerToSku mapping is empty")
    void should_returnEmpty_when_noSkuMapping() {
      var result = collector.collectClickHouseSignals(CONNECTION_ID, Map.of());

      assertThat(result.commissions()).isEmpty();
      assertThat(result.logistics()).isEmpty();
      assertThat(result.returnRates()).isEmpty();
      verifyNoInteractions(clickHouseReadRepository);
    }

    @Test
    @DisplayName("populates commission from per-SKU data when above minTransactions")
    void should_populateCommission_when_skuHasEnoughTransactions() {
      Map<Long, Long> offerToSku = Map.of(100L, 500L, 200L, 600L);

      when(clickHouseReadRepository.findAvgCommissionPct(
          eq(CONNECTION_ID), anyList(), anyInt(), anyInt()))
          .thenReturn(Map.of(
              500L, new CommissionResult(new BigDecimal("0.12"), 25),
              600L, new CommissionResult(new BigDecimal("0.15"), 30)));
      when(clickHouseReadRepository.findAvgLogisticsPerUnit(
          eq(CONNECTION_ID), anyList(), anyInt()))
          .thenReturn(Map.of());
      when(clickHouseReadRepository.findReturnRatePct(
          eq(CONNECTION_ID), anyList(), anyInt()))
          .thenReturn(Map.of());
      stubVelocityAndDaysOfCover();

      var result = collector.collectClickHouseSignals(CONNECTION_ID, offerToSku);

      assertThat(result.commissions()).containsEntry(100L, new BigDecimal("0.12"));
      assertThat(result.commissions()).containsEntry(200L, new BigDecimal("0.15"));
    }
  }

  @Nested
  @DisplayName("collectClickHouseSignals — commission category fallback")
  class CommissionCategoryFallback {

    @Test
    @DisplayName("falls back to category commission when SKU has insufficient data")
    void should_useCategoryFallback_when_skuBelowMinTransactions() {
      Map<Long, Long> offerToSku = Map.of(100L, 500L, 200L, 600L);

      when(clickHouseReadRepository.findAvgCommissionPct(
          eq(CONNECTION_ID), anyList(), anyInt(), anyInt()))
          .thenReturn(Map.of(
              500L, new CommissionResult(new BigDecimal("0.12"), 25)));

      when(clickHouseReadRepository.findCategoriesBySellerSkuIds(
          eq(CONNECTION_ID), anyList()))
          .thenReturn(Map.of(600L, "Одежда"));

      when(clickHouseReadRepository.findCategoryAvgCommissionPct(
          eq(CONNECTION_ID), eq(List.of("Одежда")), anyInt()))
          .thenReturn(Map.of("Одежда", new BigDecimal("0.18")));

      when(clickHouseReadRepository.findAvgLogisticsPerUnit(
          eq(CONNECTION_ID), anyList(), anyInt()))
          .thenReturn(Map.of());
      when(clickHouseReadRepository.findReturnRatePct(
          eq(CONNECTION_ID), anyList(), anyInt()))
          .thenReturn(Map.of());
      stubVelocityAndDaysOfCover();

      var result = collector.collectClickHouseSignals(CONNECTION_ID, offerToSku);

      assertThat(result.commissions())
          .containsEntry(100L, new BigDecimal("0.12"))
          .containsEntry(200L, new BigDecimal("0.18"));
    }

    @Test
    @DisplayName("returns null commission when both per-SKU and category have no data")
    void should_returnNull_when_noCategoryData() {
      Map<Long, Long> offerToSku = Map.of(100L, 500L);

      when(clickHouseReadRepository.findAvgCommissionPct(
          eq(CONNECTION_ID), anyList(), anyInt(), anyInt()))
          .thenReturn(Map.of());

      when(clickHouseReadRepository.findCategoriesBySellerSkuIds(
          eq(CONNECTION_ID), anyList()))
          .thenReturn(Map.of());

      when(clickHouseReadRepository.findAvgLogisticsPerUnit(
          eq(CONNECTION_ID), anyList(), anyInt()))
          .thenReturn(Map.of());
      when(clickHouseReadRepository.findReturnRatePct(
          eq(CONNECTION_ID), anyList(), anyInt()))
          .thenReturn(Map.of());
      stubVelocityAndDaysOfCover();

      var result = collector.collectClickHouseSignals(CONNECTION_ID, offerToSku);

      assertThat(result.commissions()).doesNotContainKey(100L);
    }

    @Test
    @DisplayName("skips category fallback when all SKUs have per-SKU data")
    void should_skipCategoryQuery_when_allSkusHaveData() {
      Map<Long, Long> offerToSku = Map.of(100L, 500L);

      when(clickHouseReadRepository.findAvgCommissionPct(
          eq(CONNECTION_ID), anyList(), anyInt(), anyInt()))
          .thenReturn(Map.of(
              500L, new CommissionResult(new BigDecimal("0.10"), 15)));

      when(clickHouseReadRepository.findAvgLogisticsPerUnit(
          eq(CONNECTION_ID), anyList(), anyInt()))
          .thenReturn(Map.of());
      when(clickHouseReadRepository.findReturnRatePct(
          eq(CONNECTION_ID), anyList(), anyInt()))
          .thenReturn(Map.of());
      stubVelocityAndDaysOfCover();

      collector.collectClickHouseSignals(CONNECTION_ID, offerToSku);

      verify(clickHouseReadRepository, never())
          .findCategoriesBySellerSkuIds(anyLong(), anyList());
      verify(clickHouseReadRepository, never())
          .findCategoryAvgCommissionPct(anyLong(), anyList(), anyInt());
    }
  }

  @Nested
  @DisplayName("collectClickHouseSignals — logistics")
  class Logistics {

    @Test
    @DisplayName("populates avgLogisticsPerUnit from CH data")
    void should_populateLogistics_when_dataAvailable() {
      Map<Long, Long> offerToSku = Map.of(100L, 500L);

      when(clickHouseReadRepository.findAvgCommissionPct(
          eq(CONNECTION_ID), anyList(), anyInt(), anyInt()))
          .thenReturn(Map.of());
      when(clickHouseReadRepository.findCategoriesBySellerSkuIds(
          eq(CONNECTION_ID), anyList()))
          .thenReturn(Map.of());
      when(clickHouseReadRepository.findAvgLogisticsPerUnit(
          eq(CONNECTION_ID), anyList(), anyInt()))
          .thenReturn(Map.of(500L, new BigDecimal("85.50")));
      when(clickHouseReadRepository.findReturnRatePct(
          eq(CONNECTION_ID), anyList(), anyInt()))
          .thenReturn(Map.of());
      stubVelocityAndDaysOfCover();

      var result = collector.collectClickHouseSignals(CONNECTION_ID, offerToSku);

      assertThat(result.logistics())
          .containsEntry(100L, new BigDecimal("85.50"));
    }
  }

  @Nested
  @DisplayName("collectClickHouseSignals — return rate")
  class ReturnRate {

    @Test
    @DisplayName("populates returnRatePct from CH data")
    void should_populateReturnRate_when_dataAvailable() {
      Map<Long, Long> offerToSku = Map.of(100L, 500L);

      when(clickHouseReadRepository.findAvgCommissionPct(
          eq(CONNECTION_ID), anyList(), anyInt(), anyInt()))
          .thenReturn(Map.of());
      when(clickHouseReadRepository.findCategoriesBySellerSkuIds(
          eq(CONNECTION_ID), anyList()))
          .thenReturn(Map.of());
      when(clickHouseReadRepository.findAvgLogisticsPerUnit(
          eq(CONNECTION_ID), anyList(), anyInt()))
          .thenReturn(Map.of());
      when(clickHouseReadRepository.findReturnRatePct(
          eq(CONNECTION_ID), anyList(), anyInt()))
          .thenReturn(Map.of(500L, new BigDecimal("0.035")));
      stubVelocityAndDaysOfCover();

      var result = collector.collectClickHouseSignals(CONNECTION_ID, offerToSku);

      assertThat(result.returnRates())
          .containsEntry(100L, new BigDecimal("0.035"));
    }
  }

  @Nested
  @DisplayName("collectClickHouseSignals — CH failure resilience")
  class ChFailureResilience {

    @Test
    @DisplayName("returns empty signals when ClickHouse throws exception")
    void should_returnEmptySignals_when_chFails() {
      Map<Long, Long> offerToSku = Map.of(100L, 500L);

      when(clickHouseReadRepository.findAvgCommissionPct(
          eq(CONNECTION_ID), anyList(), anyInt(), anyInt()))
          .thenThrow(new RuntimeException("CH connection refused"));

      var result = collector.collectClickHouseSignals(CONNECTION_ID, offerToSku);

      assertThat(result.commissions()).isEmpty();
      assertThat(result.logistics()).isEmpty();
      assertThat(result.returnRates()).isEmpty();
    }
  }

  @Nested
  @DisplayName("collectBatch — full integration with CH signals")
  class FullIntegration {

    @Test
    @DisplayName("populates all CH signals in PricingSignalSet")
    void should_populateAllChSignals_when_dataAvailable() {
      List<Long> offerIds = List.of(100L);
      stubPgRepos(offerIds);

      when(dataReadRepository.findSellerSkuIds(offerIds))
          .thenReturn(Map.of(100L, 500L));
      when(clickHouseReadRepository.findAvgCommissionPct(
          eq(CONNECTION_ID), anyList(), anyInt(), anyInt()))
          .thenReturn(Map.of(
              500L, new CommissionResult(new BigDecimal("0.12"), 20)));
      when(clickHouseReadRepository.findAvgLogisticsPerUnit(
          eq(CONNECTION_ID), anyList(), anyInt()))
          .thenReturn(Map.of(500L, new BigDecimal("95")));
      when(clickHouseReadRepository.findReturnRatePct(
          eq(CONNECTION_ID), anyList(), anyInt()))
          .thenReturn(Map.of(500L, new BigDecimal("0.04")));
      stubVelocityAndDaysOfCover();

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      PricingSignalSet signals = result.get(100L);
      assertThat(signals.avgCommissionPct())
          .isEqualByComparingTo(new BigDecimal("0.12"));
      assertThat(signals.avgLogisticsPerUnit())
          .isEqualByComparingTo(new BigDecimal("95"));
      assertThat(signals.returnRatePct())
          .isEqualByComparingTo(new BigDecimal("0.04"));
    }

    @Test
    @DisplayName("CH signals are null when no sellerSkuId mapping exists")
    void should_returnNullChSignals_when_noSkuMapping() {
      List<Long> offerIds = List.of(100L);
      stubPgRepos(offerIds);

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      PricingSignalSet signals = result.get(100L);
      assertThat(signals.avgCommissionPct()).isNull();
      assertThat(signals.avgLogisticsPerUnit()).isNull();
      assertThat(signals.returnRatePct()).isNull();
    }
  }

  private void stubVelocityAndDaysOfCover() {
    lenient().when(clickHouseReadRepository.findSalesVelocity(
        eq(CONNECTION_ID), anyList(), anyInt()))
        .thenReturn(Map.of());
    lenient().when(clickHouseReadRepository.findDaysOfCover(
        eq(CONNECTION_ID), anyList()))
        .thenReturn(Map.of());
  }

  private void stubPgRepos(List<Long> offerIds) {
    when(dataReadRepository.findCurrentPrices(offerIds)).thenReturn(Map.of());
    when(dataReadRepository.findCurrentCogs(offerIds)).thenReturn(Map.of());
    when(dataReadRepository.findTotalStock(offerIds)).thenReturn(Map.of());
    when(dataReadRepository.findLockedOfferIds(offerIds)).thenReturn(List.of());
    lenient().when(dataReadRepository.findDataFreshness(CONNECTION_ID))
        .thenReturn(OffsetDateTime.now());
    lenient().when(dataReadRepository.findLatestChangeDecisions(offerIds))
        .thenReturn(Map.of());
    lenient().when(dataReadRepository.findPriceReversals(eq(offerIds), any()))
        .thenReturn(Map.of());
    when(dataReadRepository.findOfferStatuses(offerIds)).thenReturn(Map.of());
    when(dataReadRepository.findMarketplaceSkus(offerIds)).thenReturn(Map.of());
    when(dataReadRepository.findSellerSkuIds(offerIds)).thenReturn(Map.of());
  }
}
