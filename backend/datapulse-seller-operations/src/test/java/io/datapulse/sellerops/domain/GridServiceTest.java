package io.datapulse.sellerops.domain;

import io.datapulse.sellerops.api.GridRowResponse;
import io.datapulse.sellerops.config.GridProperties;
import io.datapulse.sellerops.persistence.ClickHouseEnrichment;
import io.datapulse.sellerops.persistence.GridClickHouseReadRepository;
import io.datapulse.sellerops.persistence.GridPostgresReadRepository;
import io.datapulse.sellerops.persistence.GridRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GridServiceTest {

  private static final long WORKSPACE_ID = 1L;

  @Mock
  private GridPostgresReadRepository pgRepository;
  @Mock
  private GridClickHouseReadRepository chRepository;
  @Mock
  private GridProperties gridProperties;

  @InjectMocks
  private GridService service;

  @Nested
  @DisplayName("getGridPage")
  class GetGridPage {

    @Test
    void should_return_empty_page_when_pg_returns_empty() {
      Pageable pageable = PageRequest.of(0, 50);
      GridFilter filter = emptyFilter();

      when(pgRepository.findAll(WORKSPACE_ID, filter, pageable))
          .thenReturn(Page.empty(pageable));

      var result = service.getGridPage(WORKSPACE_ID, filter, pageable);

      assertThat(result.page()).isEmpty();
      verify(chRepository, never()).findEnrichment(anyList());
    }

    @Test
    void should_enrich_pg_rows_with_clickhouse_data() {
      Pageable pageable = PageRequest.of(0, 50);
      GridFilter filter = emptyFilter();

      GridRow row = GridRow.builder()
          .offerId(1L)
          .sellerSkuId(10L)
          .skuCode("SKU-001")
          .productName("Test Product")
          .marketplaceType("WB")
          .connectionName("My WB")
          .currentPrice(new BigDecimal("1000"))
          .lastSyncAt(OffsetDateTime.now())
          .build();

      when(pgRepository.findAll(WORKSPACE_ID, filter, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

      ClickHouseEnrichment enrichment = ClickHouseEnrichment.builder()
          .offerId(1L)
          .revenue30d(new BigDecimal("50000"))
          .velocity14d(new BigDecimal("3.5"))
          .daysOfCover(new BigDecimal("28"))
          .build();
      when(chRepository.findEnrichment(List.of(1L)))
          .thenReturn(Map.of(1L, enrichment));
      when(gridProperties.getFreshnessThresholdHours()).thenReturn(6);

      var result = service.getGridPage(WORKSPACE_ID, filter, pageable);

      assertThat(result.page().getContent()).hasSize(1);
      GridRowResponse response = result.page().getContent().get(0);
      assertThat(response.offerId()).isEqualTo(1L);
      assertThat(response.revenue30d()).isEqualByComparingTo(new BigDecimal("50000"));
      assertThat(response.velocity14d()).isEqualByComparingTo(new BigDecimal("3.5"));
      assertThat(response.daysOfCover()).isEqualByComparingTo(new BigDecimal("28"));
    }

    @Test
    void should_return_degraded_grid_when_clickhouse_fails() {
      Pageable pageable = PageRequest.of(0, 50);
      GridFilter filter = emptyFilter();

      GridRow row = GridRow.builder()
          .offerId(1L)
          .skuCode("SKU-001")
          .productName("Test Product")
          .lastSyncAt(OffsetDateTime.now())
          .build();

      when(pgRepository.findAll(WORKSPACE_ID, filter, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 1));
      when(chRepository.findEnrichment(anyList()))
          .thenThrow(new RuntimeException("CH unavailable"));
      when(gridProperties.getFreshnessThresholdHours()).thenReturn(6);

      var result = service.getGridPage(WORKSPACE_ID, filter, pageable);

      assertThat(result.page().getContent()).hasSize(1);
      GridRowResponse response = result.page().getContent().get(0);
      assertThat(response.offerId()).isEqualTo(1L);
      assertThat(response.revenue30d()).isNull();
      assertThat(response.velocity14d()).isNull();
      assertThat(response.daysOfCover()).isNull();
    }

    @Test
    void should_mark_freshness_as_stale_when_no_sync() {
      Pageable pageable = PageRequest.of(0, 50);
      GridFilter filter = emptyFilter();

      GridRow row = GridRow.builder()
          .offerId(1L)
          .lastSyncAt(null)
          .build();

      when(pgRepository.findAll(WORKSPACE_ID, filter, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 1));
      when(chRepository.findEnrichment(anyList())).thenReturn(Map.of());

      var result = service.getGridPage(WORKSPACE_ID, filter, pageable);

      assertThat(result.page().getContent().get(0).dataFreshness())
          .isEqualTo(DataFreshness.STALE.name());
    }

    @Test
    void should_mark_freshness_as_fresh_when_recent_sync() {
      Pageable pageable = PageRequest.of(0, 50);
      GridFilter filter = emptyFilter();

      GridRow row = GridRow.builder()
          .offerId(1L)
          .lastSyncAt(OffsetDateTime.now().minusHours(1))
          .build();

      when(pgRepository.findAll(WORKSPACE_ID, filter, pageable))
          .thenReturn(new PageImpl<>(List.of(row), pageable, 1));
      when(chRepository.findEnrichment(anyList())).thenReturn(Map.of());
      when(gridProperties.getFreshnessThresholdHours()).thenReturn(6);

      var result = service.getGridPage(WORKSPACE_ID, filter, pageable);

      assertThat(result.page().getContent().get(0).dataFreshness())
          .isEqualTo(DataFreshness.FRESH.name());
    }
  }

  private GridFilter emptyFilter() {
    return new GridFilter(null, null, null, null,
        null, null, null, null, null, null, null, null, null, null);
  }
}
