package io.datapulse.sellerops.domain;

import io.datapulse.sellerops.config.GridProperties;
import io.datapulse.sellerops.persistence.GridPostgresReadRepository;
import io.datapulse.sellerops.persistence.GridRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GridExportServiceTest {

  private static final long WORKSPACE_ID = 1L;

  @Mock
  private GridPostgresReadRepository pgRepository;
  @Mock
  private GridProperties gridProperties;

  @InjectMocks
  private GridExportService service;

  @Nested
  @DisplayName("exportCsv")
  class ExportCsv {

    @Test
    void should_write_header_and_data_row() {
      when(gridProperties.getExportMaxRows()).thenReturn(100_000);
      when(gridProperties.getExportTimeoutSeconds()).thenReturn(60);

      var row = GridRow.builder()
          .offerId(1L)
          .skuCode("SKU-001")
          .productName("Product 1")
          .marketplaceType("WB")
          .connectionName("WB Main")
          .status("ACTIVE")
          .category("Electronics")
          .currentPrice(new BigDecimal("1000"))
          .discountPrice(new BigDecimal("900"))
          .costPrice(new BigDecimal("500"))
          .marginPct(new BigDecimal("50.00"))
          .availableStock(100)
          .activePolicy("Target Margin")
          .lastDecision("CHANGE")
          .lastActionStatus("SUCCEEDED")
          .promoStatus(null)
          .manualLock(false)
          .lastSyncAt(OffsetDateTime.now())
          .build();

      when(pgRepository.findBatchForExport(eq(WORKSPACE_ID), any(), anyInt(), eq(0)))
          .thenReturn(List.of(row));

      var out = new ByteArrayOutputStream();
      GridFilter filter = emptyFilter();

      service.exportCsv(WORKSPACE_ID, filter, out);

      String csv = out.toString(StandardCharsets.UTF_8);
      assertThat(csv).contains("Артикул");
      assertThat(csv).contains("SKU-001");
      assertThat(csv).contains("Product 1");
      assertThat(csv).contains("1000");
    }

    @Test
    void should_write_empty_csv_when_no_data() {
      when(gridProperties.getExportMaxRows()).thenReturn(100_000);
      when(gridProperties.getExportTimeoutSeconds()).thenReturn(60);
      when(pgRepository.findBatchForExport(eq(WORKSPACE_ID), any(), anyInt(), eq(0)))
          .thenReturn(List.of());

      var out = new ByteArrayOutputStream();
      GridFilter filter = emptyFilter();

      service.exportCsv(WORKSPACE_ID, filter, out);

      String csv = out.toString(StandardCharsets.UTF_8);
      assertThat(csv).contains("Артикул");
      assertThat(csv.lines().count()).isEqualTo(1);
    }

    @Test
    void should_stop_when_max_rows_exceeded() {
      when(gridProperties.getExportMaxRows()).thenReturn(1);
      when(gridProperties.getExportTimeoutSeconds()).thenReturn(60);

      var row1 = GridRow.builder()
          .offerId(1L).skuCode("SKU-1").productName("P1")
          .marketplaceType("WB").connectionName("C1")
          .status("ACTIVE").manualLock(false)
          .build();
      var row2 = GridRow.builder()
          .offerId(2L).skuCode("SKU-2").productName("P2")
          .marketplaceType("WB").connectionName("C1")
          .status("ACTIVE").manualLock(false)
          .build();

      when(pgRepository.findBatchForExport(eq(WORKSPACE_ID), any(), anyInt(), eq(0)))
          .thenReturn(List.of(row1, row2));

      var out = new ByteArrayOutputStream();
      GridFilter filter = emptyFilter();

      service.exportCsv(WORKSPACE_ID, filter, out);

      verify(pgRepository, never())
          .findBatchForExport(eq(WORKSPACE_ID), any(), anyInt(), eq(500));
    }

    @Test
    void should_escape_semicolons_in_values() {
      when(gridProperties.getExportMaxRows()).thenReturn(100_000);
      when(gridProperties.getExportTimeoutSeconds()).thenReturn(60);

      var row = GridRow.builder()
          .offerId(1L).skuCode("SKU;001").productName("Product; with; semicolons")
          .marketplaceType("WB").connectionName("C1")
          .status("ACTIVE").manualLock(false)
          .build();

      when(pgRepository.findBatchForExport(eq(WORKSPACE_ID), any(), anyInt(), eq(0)))
          .thenReturn(List.of(row));

      var out = new ByteArrayOutputStream();
      GridFilter filter = emptyFilter();

      service.exportCsv(WORKSPACE_ID, filter, out);

      String csv = out.toString(StandardCharsets.UTF_8);
      assertThat(csv).contains("\"SKU;001\"");
      assertThat(csv).contains("\"Product; with; semicolons\"");
    }

    @Test
    void should_show_manual_lock_as_yes() {
      when(gridProperties.getExportMaxRows()).thenReturn(100_000);
      when(gridProperties.getExportTimeoutSeconds()).thenReturn(60);

      var row = GridRow.builder()
          .offerId(1L).skuCode("SKU-001").productName("P1")
          .marketplaceType("WB").connectionName("C1")
          .status("ACTIVE").manualLock(true)
          .build();

      when(pgRepository.findBatchForExport(eq(WORKSPACE_ID), any(), anyInt(), eq(0)))
          .thenReturn(List.of(row));

      var out = new ByteArrayOutputStream();
      GridFilter filter = emptyFilter();

      service.exportCsv(WORKSPACE_ID, filter, out);

      String csv = out.toString(StandardCharsets.UTF_8);
      assertThat(csv).contains("Да");
    }
  }

  @Nested
  @DisplayName("exportCsvByOfferIds")
  class ExportCsvByOfferIds {

    @Test
    void should_fetch_rows_by_id_and_skip_batch_export() {
      when(gridProperties.getExportMaxRows()).thenReturn(100_000);
      when(gridProperties.getExportTimeoutSeconds()).thenReturn(60);

      var row = GridRow.builder()
          .offerId(1L).skuCode("SKU-001").productName("P1")
          .marketplaceType("WB").connectionName("C1")
          .status("ACTIVE").manualLock(false)
          .build();

      when(pgRepository.findByOrderedIds(eq(WORKSPACE_ID), eq(List.of(1L))))
          .thenReturn(List.of(row));

      var out = new ByteArrayOutputStream();
      service.exportCsvByOfferIds(WORKSPACE_ID, List.of(1L), out);

      verify(pgRepository, never())
          .findBatchForExport(anyLong(), any(), anyInt(), anyInt());
      String csv = out.toString(StandardCharsets.UTF_8);
      assertThat(csv).contains("Артикул");
      assertThat(csv).contains("SKU-001");
    }
  }

  private GridFilter emptyFilter() {
    return new GridFilter(null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null);
  }
}
