package io.datapulse.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.datapulse.pricing.persistence.PricingDataReadRepository;

@ExtendWith(MockitoExtension.class)
class PricingSignalCollectorTest {

  @Mock
  private PricingDataReadRepository dataReadRepository;

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
    }
  }

  @Nested
  @DisplayName("collectBatch — with offers")
  class WithOffers {

    @Test
    @DisplayName("assembles signals for each offer from repository data")
    void should_assembleSignals_when_offersProvided() {
      List<Long> offerIds = List.of(100L, 200L);

      when(dataReadRepository.findCurrentPrices(offerIds))
          .thenReturn(Map.of(100L, new BigDecimal("999"), 200L, new BigDecimal("1500")));
      when(dataReadRepository.findCurrentCogs(offerIds))
          .thenReturn(Map.of(100L, new BigDecimal("400")));
      when(dataReadRepository.findTotalStock(offerIds))
          .thenReturn(Map.of(100L, 50, 200L, 0));
      when(dataReadRepository.findLockedOfferIds(offerIds))
          .thenReturn(List.of(200L));
      when(dataReadRepository.findDataFreshness(CONNECTION_ID))
          .thenReturn(OffsetDateTime.now());
      when(dataReadRepository.findLatestChangeDecisions(offerIds))
          .thenReturn(Map.of());
      when(dataReadRepository.findPriceReversals(eq(offerIds), any()))
          .thenReturn(Map.of());

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      assertThat(result).hasSize(2);

      PricingSignalSet signals100 = result.get(100L);
      assertThat(signals100.currentPrice()).isEqualByComparingTo(new BigDecimal("999"));
      assertThat(signals100.cogs()).isEqualByComparingTo(new BigDecimal("400"));
      assertThat(signals100.availableStock()).isEqualTo(50);
      assertThat(signals100.manualLockActive()).isFalse();

      PricingSignalSet signals200 = result.get(200L);
      assertThat(signals200.currentPrice()).isEqualByComparingTo(new BigDecimal("1500"));
      assertThat(signals200.cogs()).isNull();
      assertThat(signals200.availableStock()).isEqualTo(0);
      assertThat(signals200.manualLockActive()).isTrue();
    }

    @Test
    @DisplayName("handles null COGS and null stock gracefully")
    void should_handleNullSignals_when_dataNotAvailable() {
      List<Long> offerIds = List.of(300L);

      when(dataReadRepository.findCurrentPrices(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findCurrentCogs(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findTotalStock(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findLockedOfferIds(offerIds)).thenReturn(List.of());
      when(dataReadRepository.findDataFreshness(CONNECTION_ID)).thenReturn(null);
      when(dataReadRepository.findLatestChangeDecisions(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findPriceReversals(eq(offerIds), any())).thenReturn(Map.of());

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      PricingSignalSet signals = result.get(300L);
      assertThat(signals.currentPrice()).isNull();
      assertThat(signals.cogs()).isNull();
      assertThat(signals.availableStock()).isNull();
      assertThat(signals.manualLockActive()).isFalse();
      assertThat(signals.dataFreshnessAt()).isNull();
    }

    @Test
    @DisplayName("calls all repository methods with correct offer IDs")
    void should_callAllRepositoryMethods_when_offersProvided() {
      List<Long> offerIds = List.of(1L);

      when(dataReadRepository.findCurrentPrices(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findCurrentCogs(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findTotalStock(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findLockedOfferIds(offerIds)).thenReturn(List.of());
      when(dataReadRepository.findDataFreshness(CONNECTION_ID)).thenReturn(null);
      when(dataReadRepository.findLatestChangeDecisions(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findPriceReversals(eq(offerIds), any())).thenReturn(Map.of());

      collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      verify(dataReadRepository).findCurrentPrices(offerIds);
      verify(dataReadRepository).findCurrentCogs(offerIds);
      verify(dataReadRepository).findTotalStock(offerIds);
      verify(dataReadRepository).findLockedOfferIds(offerIds);
      verify(dataReadRepository).findDataFreshness(CONNECTION_ID);
      verify(dataReadRepository).findLatestChangeDecisions(offerIds);
      verify(dataReadRepository).findPriceReversals(eq(offerIds), any());
    }

    @Test
    @DisplayName("populates lastPriceChangeAt from latest change decisions")
    void should_populateLastChange_when_decisionExists() {
      List<Long> offerIds = List.of(100L);
      OffsetDateTime lastChange = OffsetDateTime.now().minusHours(12);

      when(dataReadRepository.findCurrentPrices(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findCurrentCogs(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findTotalStock(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findLockedOfferIds(offerIds)).thenReturn(List.of());
      when(dataReadRepository.findDataFreshness(CONNECTION_ID)).thenReturn(null);
      when(dataReadRepository.findLatestChangeDecisions(offerIds))
          .thenReturn(Map.of(100L, lastChange));
      when(dataReadRepository.findPriceReversals(eq(offerIds), any())).thenReturn(Map.of());

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      assertThat(result.get(100L).lastPriceChangeAt()).isEqualTo(lastChange);
    }

    @Test
    @DisplayName("populates reversals count from price reversals query")
    void should_populateReversals_when_reversalsExist() {
      List<Long> offerIds = List.of(100L);

      when(dataReadRepository.findCurrentPrices(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findCurrentCogs(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findTotalStock(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findLockedOfferIds(offerIds)).thenReturn(List.of());
      when(dataReadRepository.findDataFreshness(CONNECTION_ID)).thenReturn(null);
      when(dataReadRepository.findLatestChangeDecisions(offerIds)).thenReturn(Map.of());
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

      when(dataReadRepository.findCurrentPrices(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findCurrentCogs(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findTotalStock(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findLockedOfferIds(offerIds)).thenReturn(List.of());
      when(dataReadRepository.findDataFreshness(CONNECTION_ID)).thenReturn(freshness);
      when(dataReadRepository.findLatestChangeDecisions(offerIds)).thenReturn(Map.of());
      when(dataReadRepository.findPriceReversals(eq(offerIds), any())).thenReturn(Map.of());

      Map<Long, PricingSignalSet> result =
          collector.collectBatch(offerIds, CONNECTION_ID, VOLATILITY_DAYS);

      assertThat(result.get(100L).dataFreshnessAt()).isEqualTo(freshness);
      assertThat(result.get(200L).dataFreshnessAt()).isEqualTo(freshness);
    }
  }
}
