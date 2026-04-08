package io.datapulse.sellerops.domain;

import io.datapulse.common.exception.NotFoundException;
import io.datapulse.sellerops.api.OfferDetailResponse;
import io.datapulse.sellerops.config.GridProperties;
import io.datapulse.sellerops.persistence.ClickHouseEnrichment;
import io.datapulse.sellerops.persistence.GridClickHouseReadRepository;
import io.datapulse.sellerops.persistence.OfferDetailJdbcRepository;
import io.datapulse.sellerops.persistence.OfferDetailRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferServiceTest {

  private static final long WORKSPACE_ID = 1L;
  private static final long OFFER_ID = 100L;

  @Mock
  private OfferDetailJdbcRepository detailRepository;
  @Mock
  private GridClickHouseReadRepository chRepository;
  @Mock
  private GridProperties gridProperties;

  @InjectMocks
  private OfferService service;

  @Nested
  @DisplayName("getOfferDetail")
  class GetOfferDetail {

    @Test
    void should_return_full_detail_with_enrichment() {
      var row = buildOfferRow(true, true, true, true);
      row.setLockId(50L);
      row.setLockedPrice(new BigDecimal("999"));
      row.setLockReason("Test lock");
      row.setLockedAt(OffsetDateTime.now().minusDays(1));
      when(detailRepository.findById(WORKSPACE_ID, OFFER_ID))
          .thenReturn(Optional.of(row));

      var ch = ClickHouseEnrichment.builder()
          .offerId(OFFER_ID)
          .revenue30d(new BigDecimal("50000"))
          .netPnl30d(new BigDecimal("15000"))
          .velocity14d(new BigDecimal("3.5"))
          .returnRatePct(new BigDecimal("2.1"))
          .daysOfCover(new BigDecimal("45"))
          .stockRisk("LOW")
          .build();
      when(chRepository.findEnrichment(List.of(OFFER_ID)))
          .thenReturn(Map.of(OFFER_ID, ch));
      when(gridProperties.getFreshnessThresholdHours()).thenReturn(6);

      OfferDetailResponse result = service.getOfferDetail(WORKSPACE_ID, OFFER_ID);

      assertThat(result.offerId()).isEqualTo(OFFER_ID);
      assertThat(result.skuCode()).isEqualTo("SKU-001");
      assertThat(result.revenue30d()).isEqualTo(new BigDecimal("50000"));
      assertThat(result.activePolicy()).isNotNull();
      assertThat(result.activePolicy().name()).isEqualTo("Target Margin");
      assertThat(result.lastDecision()).isNotNull();
      assertThat(result.lastAction()).isNotNull();
      assertThat(result.promoStatus()).isNotNull();
      assertThat(result.promoStatus().participating()).isTrue();
      assertThat(result.manualLock()).isNotNull();
      assertThat(result.dataFreshness()).isEqualTo(DataFreshness.FRESH.name());
    }

    @Test
    void should_return_null_sections_when_no_related_data() {
      var row = buildOfferRow(false, false, false, false);
      when(detailRepository.findById(WORKSPACE_ID, OFFER_ID))
          .thenReturn(Optional.of(row));
      when(chRepository.findEnrichment(List.of(OFFER_ID)))
          .thenReturn(Map.of());
      when(gridProperties.getFreshnessThresholdHours()).thenReturn(6);

      OfferDetailResponse result = service.getOfferDetail(WORKSPACE_ID, OFFER_ID);

      assertThat(result.activePolicy()).isNull();
      assertThat(result.lastDecision()).isNull();
      assertThat(result.lastAction()).isNull();
      assertThat(result.promoStatus()).isNull();
      assertThat(result.manualLock()).isNull();
      assertThat(result.revenue30d()).isNull();
    }

    @Test
    void should_throw_not_found_when_offer_missing() {
      when(detailRepository.findById(WORKSPACE_ID, OFFER_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getOfferDetail(WORKSPACE_ID, OFFER_ID))
          .isInstanceOf(NotFoundException.class);
    }

    @Test
    void should_gracefully_degrade_when_clickhouse_fails() {
      var row = buildOfferRow(false, false, false, false);
      when(detailRepository.findById(WORKSPACE_ID, OFFER_ID))
          .thenReturn(Optional.of(row));
      when(chRepository.findEnrichment(anyList()))
          .thenThrow(new RuntimeException("ClickHouse down"));
      when(gridProperties.getFreshnessThresholdHours()).thenReturn(6);

      OfferDetailResponse result = service.getOfferDetail(WORKSPACE_ID, OFFER_ID);

      assertThat(result.offerId()).isEqualTo(OFFER_ID);
      assertThat(result.revenue30d()).isNull();
      assertThat(result.netPnl30d()).isNull();
    }

    @Test
    void should_return_stale_when_sync_is_old() {
      var row = buildOfferRow(false, false, false, false);
      row.setLastSyncAt(OffsetDateTime.now().minusHours(12));
      when(detailRepository.findById(WORKSPACE_ID, OFFER_ID))
          .thenReturn(Optional.of(row));
      when(chRepository.findEnrichment(List.of(OFFER_ID)))
          .thenReturn(Map.of());
      when(gridProperties.getFreshnessThresholdHours()).thenReturn(6);

      OfferDetailResponse result = service.getOfferDetail(WORKSPACE_ID, OFFER_ID);

      assertThat(result.dataFreshness()).isEqualTo(DataFreshness.STALE.name());
    }

    @Test
    void should_return_stale_when_sync_is_null() {
      var row = buildOfferRow(false, false, false, false);
      row.setLastSyncAt(null);
      when(detailRepository.findById(WORKSPACE_ID, OFFER_ID))
          .thenReturn(Optional.of(row));
      when(chRepository.findEnrichment(List.of(OFFER_ID)))
          .thenReturn(Map.of());

      OfferDetailResponse result = service.getOfferDetail(WORKSPACE_ID, OFFER_ID);

      assertThat(result.dataFreshness()).isEqualTo(DataFreshness.STALE.name());
    }

    @Test
    void should_map_lock_info_when_lock_present() {
      var row = buildOfferRow(false, false, false, false);
      row.setLockId(50L);
      row.setLockedPrice(new BigDecimal("999"));
      row.setLockReason("Ожидание акции");
      row.setLockedAt(OffsetDateTime.now().minusDays(1));
      when(detailRepository.findById(WORKSPACE_ID, OFFER_ID))
          .thenReturn(Optional.of(row));
      when(chRepository.findEnrichment(List.of(OFFER_ID)))
          .thenReturn(Map.of());
      when(gridProperties.getFreshnessThresholdHours()).thenReturn(6);

      OfferDetailResponse result = service.getOfferDetail(WORKSPACE_ID, OFFER_ID);

      assertThat(result.manualLock()).isNotNull();
      assertThat(result.manualLock().lockedPrice())
          .isEqualByComparingTo(new BigDecimal("999"));
      assertThat(result.manualLock().reason()).isEqualTo("Ожидание акции");
      assertThat(result.manualLock().lockedAt()).isNotNull();
    }

    @Test
    void should_return_null_lock_when_no_active_lock() {
      var row = buildOfferRow(false, false, false, false);
      when(detailRepository.findById(WORKSPACE_ID, OFFER_ID))
          .thenReturn(Optional.of(row));
      when(chRepository.findEnrichment(List.of(OFFER_ID)))
          .thenReturn(Map.of());
      when(gridProperties.getFreshnessThresholdHours()).thenReturn(6);

      OfferDetailResponse result = service.getOfferDetail(WORKSPACE_ID, OFFER_ID);

      assertThat(result.manualLock()).isNull();
    }

    @Test
    void should_map_simulated_price_fields() {
      var row = buildOfferRow(false, false, false, false);
      row.setSimulatedPrice(new BigDecimal("1200"));
      row.setSimulatedDeltaPct(new BigDecimal("20.00"));
      when(detailRepository.findById(WORKSPACE_ID, OFFER_ID))
          .thenReturn(Optional.of(row));
      when(chRepository.findEnrichment(List.of(OFFER_ID)))
          .thenReturn(Map.of());
      when(gridProperties.getFreshnessThresholdHours()).thenReturn(6);

      OfferDetailResponse result = service.getOfferDetail(WORKSPACE_ID, OFFER_ID);

      assertThat(result.simulatedPrice())
          .isEqualByComparingTo(new BigDecimal("1200"));
      assertThat(result.simulatedDeltaPct())
          .isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    void should_return_fresh_when_sync_is_exactly_at_threshold() {
      var row = buildOfferRow(false, false, false, false);
      row.setLastSyncAt(OffsetDateTime.now().minusHours(6));
      when(detailRepository.findById(WORKSPACE_ID, OFFER_ID))
          .thenReturn(Optional.of(row));
      when(chRepository.findEnrichment(List.of(OFFER_ID)))
          .thenReturn(Map.of());
      when(gridProperties.getFreshnessThresholdHours()).thenReturn(6);

      OfferDetailResponse result = service.getOfferDetail(WORKSPACE_ID, OFFER_ID);

      assertThat(result.dataFreshness()).isEqualTo(DataFreshness.FRESH.name());
    }

    @Test
    void should_map_non_participating_promo_as_not_participating() {
      var row = buildOfferRow(false, false, false, false);
      row.setPromoParticipationStatus("ELIGIBLE");
      row.setPromoCampaignName("Летняя распродажа");
      row.setPromoPrice(new BigDecimal("700"));
      row.setPromoEndsAt(OffsetDateTime.now().plusDays(3));
      when(detailRepository.findById(WORKSPACE_ID, OFFER_ID))
          .thenReturn(Optional.of(row));
      when(chRepository.findEnrichment(List.of(OFFER_ID)))
          .thenReturn(Map.of());
      when(gridProperties.getFreshnessThresholdHours()).thenReturn(6);

      OfferDetailResponse result = service.getOfferDetail(WORKSPACE_ID, OFFER_ID);

      assertThat(result.promoStatus()).isNotNull();
      assertThat(result.promoStatus().participating()).isFalse();
      assertThat(result.promoStatus().campaignName()).isEqualTo("Летняя распродажа");
    }

    @Test
    void should_map_partial_enrichment_with_null_fields() {
      var row = buildOfferRow(false, false, false, false);
      when(detailRepository.findById(WORKSPACE_ID, OFFER_ID))
          .thenReturn(Optional.of(row));

      var ch = ClickHouseEnrichment.builder()
          .offerId(OFFER_ID)
          .daysOfCover(new BigDecimal("45"))
          .stockRisk("LOW")
          .revenue30d(null)
          .netPnl30d(null)
          .velocity14d(new BigDecimal("3.5"))
          .returnRatePct(null)
          .build();
      when(chRepository.findEnrichment(List.of(OFFER_ID)))
          .thenReturn(Map.of(OFFER_ID, ch));
      when(gridProperties.getFreshnessThresholdHours()).thenReturn(6);

      OfferDetailResponse result = service.getOfferDetail(WORKSPACE_ID, OFFER_ID);

      assertThat(result.daysOfCover())
          .isEqualByComparingTo(new BigDecimal("45"));
      assertThat(result.stockRisk()).isEqualTo("LOW");
      assertThat(result.revenue30d()).isNull();
      assertThat(result.netPnl30d()).isNull();
      assertThat(result.velocity14d())
          .isEqualByComparingTo(new BigDecimal("3.5"));
      assertThat(result.returnRatePct()).isNull();
    }
  }

  private OfferDetailRow buildOfferRow(boolean withPolicy, boolean withDecision,
                                        boolean withAction, boolean withPromo) {
    var builder = OfferDetailRow.builder()
        .offerId(OFFER_ID)
        .sellerSkuId(200L)
        .skuCode("SKU-001")
        .productName("Test Product")
        .marketplaceType("WB")
        .connectionName("WB Main")
        .status("ACTIVE")
        .category("Electronics")
        .currentPrice(new BigDecimal("1000"))
        .discountPrice(new BigDecimal("900"))
        .costPrice(new BigDecimal("500"))
        .marginPct(new BigDecimal("50.00"))
        .availableStock(100)
        .lastSyncAt(OffsetDateTime.now().minusHours(1));

    if (withPolicy) {
      builder.policyId(10L)
          .policyName("Target Margin")
          .strategyType("TARGET_MARGIN")
          .policyExecutionMode("SEMI_AUTO");
    }

    if (withDecision) {
      builder.decisionId(20L)
          .decisionType("CHANGE")
          .decisionCurrentPrice(new BigDecimal("1000"))
          .decisionTargetPrice(new BigDecimal("1100"))
          .decisionExplanation("Margin below target")
          .decisionCreatedAt(OffsetDateTime.now().minusHours(2));
    }

    if (withAction) {
      builder.actionId(30L)
          .actionStatus("SUCCEEDED")
          .actionTargetPrice(new BigDecimal("1100"))
          .actionExecutionMode("LIVE")
          .actionCreatedAt(OffsetDateTime.now().minusHours(1));
    }

    if (withPromo) {
      builder.promoParticipationStatus("PARTICIPATING")
          .promoCampaignName("Spring Sale")
          .promoPrice(new BigDecimal("850"))
          .promoEndsAt(OffsetDateTime.now().plusDays(7));
    }

    return builder.build();
  }
}
