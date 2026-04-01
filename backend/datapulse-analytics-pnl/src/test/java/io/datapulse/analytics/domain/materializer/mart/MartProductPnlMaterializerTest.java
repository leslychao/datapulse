package io.datapulse.analytics.domain.materializer.mart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.datapulse.analytics.domain.MaterializationPhase;
import io.datapulse.analytics.persistence.MaterializationJdbc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("MartProductPnlMaterializer")
class MartProductPnlMaterializerTest {

  @Mock private MaterializationJdbc jdbc;
  @Mock private JdbcTemplate chTemplate;

  @InjectMocks
  private MartProductPnlMaterializer materializer;

  @Test
  @DisplayName("should report table name as 'mart_product_pnl'")
  void should_returnTableName() {
    assertThat(materializer.tableName()).isEqualTo("mart_product_pnl");
  }

  @Test
  @DisplayName("should belong to MART phase")
  void should_returnMartPhase() {
    assertThat(materializer.phase()).isEqualTo(MaterializationPhase.MART);
  }

  @Nested
  @DisplayName("materializeFull")
  class Full {

    @Test
    @DisplayName("should truncate before full materialization")
    void should_truncateFirst_when_fullRun() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

      materializer.materializeFull();

      verify(chTemplate).execute("TRUNCATE TABLE mart_product_pnl");
    }

    @Test
    @DisplayName("SQL should contain marketplace_pnl formula (sum of 11 signed measures)")
    void should_containMarketplacePnlFormula_when_sqlGenerated() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

      materializer.materializeFull();

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate, atLeast(2)).execute(sqlCaptor.capture());

      String sql = sqlCaptor.getAllValues().stream()
          .filter(s -> s.contains("INSERT"))
          .findFirst()
          .orElse("");

      assertThat(sql).contains("marketplace_pnl");
      assertThat(sql).contains("revenue_amount + marketplace_commission_amount + acquiring_commission_amount");
    }

    @Test
    @DisplayName("SQL should compute full_pnl = marketplace_pnl - advertising - net_cogs")
    void should_containFullPnlFormula_when_sqlGenerated() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

      materializer.materializeFull();

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate, atLeast(2)).execute(sqlCaptor.capture());

      String sql = sqlCaptor.getAllValues().stream()
          .filter(s -> s.contains("INSERT"))
          .findFirst()
          .orElse("");

      assertThat(sql).contains("full_pnl");
    }

    @Test
    @DisplayName("SQL should contain revenue-ratio COGS netting at product level")
    void should_containCogsNetting_when_sqlGenerated() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

      materializer.materializeFull();

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate, atLeast(2)).execute(sqlCaptor.capture());

      String sql = sqlCaptor.getAllValues().stream()
          .filter(s -> s.contains("INSERT"))
          .findFirst()
          .orElse("");

      assertThat(sql).contains("product_refund_ratio");
      assertThat(sql).contains("net_cogs");
      assertThat(sql).contains("gross_cogs");
    }

    @Test
    @DisplayName("SQL should union POSTING + PRODUCT + ACCOUNT attribution levels")
    void should_containThreeSourceLevels_when_sqlGenerated() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

      materializer.materializeFull();

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate, atLeast(2)).execute(sqlCaptor.capture());

      String sql = sqlCaptor.getAllValues().stream()
          .filter(s -> s.contains("INSERT"))
          .findFirst()
          .orElse("");

      assertThat(sql).contains("UNION ALL");
      assertThat(sql).contains("attribution_level = 'PRODUCT'");
      assertThat(sql).contains("attribution_level = 'ACCOUNT'");
    }

    @Test
    @DisplayName("SQL should handle division by zero for revenue in refund ratio")
    void should_guardDivisionByZero_when_revenueIsZero() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

      materializer.materializeFull();

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate, atLeast(2)).execute(sqlCaptor.capture());

      String sql = sqlCaptor.getAllValues().stream()
          .filter(s -> s.contains("INSERT"))
          .findFirst()
          .orElse("");

      assertThat(sql).contains("if(revenue_amount != 0");
    }

    @Test
    @DisplayName("SQL should return null COGS when cost profile missing")
    void should_returnNullCogs_when_noCostProfile() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

      materializer.materializeFull();

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate, atLeast(2)).execute(sqlCaptor.capture());

      String sql = sqlCaptor.getAllValues().stream()
          .filter(s -> s.contains("INSERT"))
          .findFirst()
          .orElse("");

      assertThat(sql).contains("'NO_COST_PROFILE'");
      assertThat(sql).contains("NULL AS gross_cogs");
    }
  }

  @Nested
  @DisplayName("materializeIncremental")
  class Incremental {

    @Test
    @DisplayName("should delegate to materializeFull for correctness")
    void should_delegateToFull_when_incrementalRun() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

      materializer.materializeIncremental(42L);

      verify(chTemplate).execute("TRUNCATE TABLE mart_product_pnl");
    }
  }
}
