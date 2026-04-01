package io.datapulse.analytics.domain.materializer.dim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
@DisplayName("DimCategoryMaterializer")
class DimCategoryMaterializerTest {

  @Mock private MaterializationJdbc jdbc;
  @Mock private AnalyticsProperties properties;
  @Mock private JdbcTemplate chTemplate;
  @Mock private NamedParameterJdbcTemplate pgTemplate;

  @InjectMocks
  private DimCategoryMaterializer materializer;

  @Test
  @DisplayName("should report table name as 'dim_category'")
  void should_returnTableName() {
    assertThat(materializer.tableName()).isEqualTo("dim_category");
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
    @DisplayName("should truncate and process in batches")
    void should_truncateAndBatch_when_fullRun() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(jdbc.pg()).thenReturn(pgTemplate);
      when(properties.batchSize()).thenReturn(5000);
      when(pgTemplate.queryForList(anyString(), any(Map.class)))
          .thenReturn(List.of());

      materializer.materializeFull();

      verify(chTemplate).execute("TRUNCATE TABLE dim_category");
    }
  }

  @Nested
  @DisplayName("materializeIncremental")
  class Incremental {

    @Test
    @DisplayName("should skip when no categories affected by job")
    void should_skip_when_noAffectedRows() {
      when(jdbc.pg()).thenReturn(pgTemplate);
      when(pgTemplate.queryForList(anyString(), any(Map.class)))
          .thenReturn(List.of());

      materializer.materializeIncremental(42L);

      verify(jdbc.pg()).queryForList(anyString(), any(Map.class));
    }
  }
}
