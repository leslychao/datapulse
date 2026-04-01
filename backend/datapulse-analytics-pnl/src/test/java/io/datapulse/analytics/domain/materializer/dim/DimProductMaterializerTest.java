package io.datapulse.analytics.domain.materializer.dim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.datapulse.analytics.config.AnalyticsProperties;
import io.datapulse.analytics.domain.MaterializationPhase;
import io.datapulse.analytics.persistence.MaterializationJdbc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("DimProductMaterializer")
class DimProductMaterializerTest {

  @Mock private MaterializationJdbc jdbc;
  @Mock private AnalyticsProperties properties;
  @Mock private JdbcTemplate chTemplate;
  @Mock private NamedParameterJdbcTemplate pgTemplate;

  @InjectMocks
  private DimProductMaterializer materializer;

  private static Map<String, Object> testProductRow() {
    var row = new HashMap<String, Object>();
    row.put("product_id", 1L);
    row.put("connection_id", 10);
    row.put("source_platform", "WB");
    row.put("seller_sku_id", 100L);
    row.put("product_master_id", 200L);
    row.put("sku_code", "SKU001");
    row.put("marketplace_sku", "WB-001");
    row.put("product_name", "Test Product");
    row.put("brand", "TestBrand");
    row.put("category", "Shoes");
    row.put("status", "ACTIVE");
    return row;
  }

  @Test
  @DisplayName("should report table name as 'dim_product'")
  void should_returnTableName() {
    assertThat(materializer.tableName()).isEqualTo("dim_product");
  }

  @Test
  @DisplayName("should belong to DIMENSION phase")
  void should_returnDimensionPhase() {
    assertThat(materializer.phase()).isEqualTo(MaterializationPhase.DIMENSION);
  }

  @Nested
  @DisplayName("materializeFull")
  class Full {

    @Test
    @DisplayName("should truncate CH table before materialization")
    void should_truncateFirst_when_fullRun() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(jdbc.pg()).thenReturn(pgTemplate);
      when(properties.batchSize()).thenReturn(5000);
      when(pgTemplate.queryForList(anyString(), any(Map.class)))
          .thenReturn(List.of());

      materializer.materializeFull();

      verify(chTemplate).execute("TRUNCATE TABLE dim_product");
    }

    @Test
    @DisplayName("should process rows in batches until empty result")
    void should_batchProcess_when_rowsExist() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(jdbc.pg()).thenReturn(pgTemplate);
      when(properties.batchSize()).thenReturn(2);

      Map<String, Object> row = testProductRow();

      when(pgTemplate.queryForList(anyString(), any(Map.class)))
          .thenReturn(List.of(row))
          .thenReturn(List.of());

      materializer.materializeFull();

      verify(chTemplate).batchUpdate(anyString(), eq(List.of(row)), eq(1), any());
    }
  }

  @Nested
  @DisplayName("materializeIncremental")
  class Incremental {

    @Test
    @DisplayName("should skip batch insert when no affected rows")
    void should_skip_when_noAffectedRows() {
      when(jdbc.pg()).thenReturn(pgTemplate);
      when(pgTemplate.queryForList(anyString(), any(Map.class)))
          .thenReturn(List.of());

      materializer.materializeIncremental(42L);

      verify(jdbc.pg()).queryForList(anyString(), any(Map.class));
    }

    @Test
    @DisplayName("should insert affected rows with correct jobExecutionId filter")
    void should_insertAffected_when_rowsMatchJob() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(jdbc.pg()).thenReturn(pgTemplate);

      Map<String, Object> row = testProductRow();

      when(pgTemplate.queryForList(anyString(), eq(Map.of("jobExecutionId", 77L))))
          .thenReturn(List.of(row));

      materializer.materializeIncremental(77L);

      verify(chTemplate).batchUpdate(anyString(), eq(List.of(row)), eq(1), any());
    }
  }
}
