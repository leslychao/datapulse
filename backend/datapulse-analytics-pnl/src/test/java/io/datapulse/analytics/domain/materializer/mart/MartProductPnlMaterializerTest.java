package io.datapulse.analytics.domain.materializer.mart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.datapulse.analytics.domain.MaterializationPhase;
import io.datapulse.analytics.persistence.MaterializationJdbc;
import java.util.function.Consumer;
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
    @DisplayName("should use fullMaterializeWithSwap for mart_product_pnl")
    void should_useSwap_when_fullRun() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeFull();

      verify(jdbc).fullMaterializeWithSwap(eq("mart_product_pnl"), any());
    }

    @Test
    @DisplayName("SQL should contain marketplace_pnl formula (sum of 11 signed measures)")
    void should_containMarketplacePnlFormula_when_sqlGenerated() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeFull();

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate, atLeast(1)).execute(sqlCaptor.capture());

      String sql = sqlCaptor.getAllValues().stream()
          .filter(s -> s.contains("INSERT INTO"))
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
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeFull();

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate, atLeast(1)).execute(sqlCaptor.capture());

      String sql = sqlCaptor.getAllValues().stream()
          .filter(s -> s.contains("INSERT INTO"))
          .findFirst()
          .orElse("");

      assertThat(sql).contains("full_pnl");
    }

    @Test
    @DisplayName("SQL should contain revenue-ratio COGS netting at product level")
    void should_containCogsNetting_when_sqlGenerated() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeFull();

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate, atLeast(1)).execute(sqlCaptor.capture());

      String sql = sqlCaptor.getAllValues().stream()
          .filter(s -> s.contains("INSERT INTO"))
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
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeFull();

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate, atLeast(1)).execute(sqlCaptor.capture());

      String sql = sqlCaptor.getAllValues().stream()
          .filter(s -> s.contains("INSERT INTO"))
          .findFirst()
          .orElse("");

      assertThat(sql).contains("UNION ALL");
      assertThat(sql).contains("attribution_level = 'PRODUCT'");
      assertThat(sql).contains("attribution_level = 'ACCOUNT'");
    }

    @Test
    @DisplayName("SQL should LEFT JOIN fact_advertising via dim_product for advertising_cost")
    void should_joinFactAdvertising_when_sqlGenerated() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeFull();

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate, atLeast(1)).execute(sqlCaptor.capture());

      String sql = sqlCaptor.getAllValues().stream()
          .filter(s -> s.contains("INSERT INTO"))
          .findFirst()
          .orElse("");

      assertThat(sql).contains("fact_advertising");
      assertThat(sql).contains("ad_agg");
      assertThat(sql).contains("coalesce(ad_agg.ad_spend, toDecimal64(0, 2)) AS advertising_cost");
      assertThat(sql).contains("dp.seller_sku_id");
      assertThat(sql).contains("fa.marketplace_sku = dp.marketplace_sku");
    }

    @Test
    @DisplayName("SQL should compute full_pnl using real advertising_cost from ad_agg")
    void should_computeFullPnlWithAdvertisingCost_when_sqlGenerated() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeFull();

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate, atLeast(1)).execute(sqlCaptor.capture());

      String sql = sqlCaptor.getAllValues().stream()
          .filter(s -> s.contains("INSERT INTO"))
          .findFirst()
          .orElse("");

      assertThat(sql).contains("base.marketplace_pnl");
      assertThat(sql).contains("- coalesce(ad_agg.ad_spend, toDecimal64(0, 2))");
      assertThat(sql).contains("- base.net_cogs");
    }

    @Test
    @DisplayName("SQL should handle division by zero for revenue in refund ratio")
    void should_guardDivisionByZero_when_revenueIsZero() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeFull();

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate, atLeast(1)).execute(sqlCaptor.capture());

      String sql = sqlCaptor.getAllValues().stream()
          .filter(s -> s.contains("INSERT INTO"))
          .findFirst()
          .orElse("");

      assertThat(sql).contains("if(revenue_amount != 0");
    }

    @Test
    @DisplayName("SQL should return null COGS when cost profile missing")
    void should_returnNullCogs_when_noCostProfile() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeFull();

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate, atLeast(1)).execute(sqlCaptor.capture());

      String sql = sqlCaptor.getAllValues().stream()
          .filter(s -> s.contains("INSERT INTO"))
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
    @DisplayName("should delegate to materializeFull via fullMaterializeWithSwap")
    void should_delegateToFull_when_incrementalRun() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeIncremental(42L);

      verify(jdbc).fullMaterializeWithSwap(eq("mart_product_pnl"), any());
    }
  }
}
