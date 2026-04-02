package io.datapulse.analytics.domain.materializer.mart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.datapulse.analytics.config.AnalyticsQueryProperties;
import io.datapulse.analytics.config.AnalyticsQueryProperties.InventoryProperties;
import io.datapulse.analytics.domain.MaterializationPhase;
import io.datapulse.analytics.persistence.MaterializationJdbc;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("MartInventoryAnalysisMaterializer")
class MartInventoryAnalysisMaterializerTest {

  @Mock private MaterializationJdbc jdbc;
  @Mock private JdbcTemplate chTemplate;

  private MartInventoryAnalysisMaterializer materializer;

  @BeforeEach
  void setUp() {
    var inventoryProps = new InventoryProperties(14, 30, 7);
    var queryProps = new AnalyticsQueryProperties(inventoryProps, null);
    materializer = new MartInventoryAnalysisMaterializer(jdbc, queryProps);
  }

  @Test
  @DisplayName("should report table name as 'mart_inventory_analysis'")
  void should_returnTableName() {
    assertThat(materializer.tableName()).isEqualTo("mart_inventory_analysis");
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
    @DisplayName("should use fullMaterializeWithSwap for mart_inventory_analysis")
    void should_useSwap_when_fullRun() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeFull();

      verify(jdbc).fullMaterializeWithSwap(eq("mart_inventory_analysis"), any());
    }

    @Test
    @DisplayName("SQL should contain days_of_cover calculation with division guard")
    void should_containDaysOfCover_when_sqlGenerated() {
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

      assertThat(sql).contains("days_of_cover");
      assertThat(sql).contains("avg_daily_sales > 0");
    }

    @Test
    @DisplayName("SQL should contain stock_out_risk classification (CRITICAL/WARNING/NORMAL)")
    void should_containStockOutRisk_when_sqlGenerated() {
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

      assertThat(sql).contains("stock_out_risk");
      assertThat(sql).contains("'CRITICAL'");
      assertThat(sql).contains("'WARNING'");
      assertThat(sql).contains("'NORMAL'");
    }

    @Test
    @DisplayName("SQL should use inventory properties for threshold calculations")
    void should_useProperties_when_sqlFormatted() {
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

      // leadTimeDays = 7, leadTimeDays * 2 = 14, targetDaysOfCover = 30
      assertThat(sql).contains("< 7");
      assertThat(sql).contains("< 14");
    }

    @Test
    @DisplayName("SQL should calculate frozen capital based on excess stock")
    void should_containFrozenCapital_when_sqlGenerated() {
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

      assertThat(sql).contains("frozen_capital");
      assertThat(sql).contains("cost_price");
    }

    @Test
    @DisplayName("SQL should calculate recommended_replenishment")
    void should_containReplenishment_when_sqlGenerated() {
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

      assertThat(sql).contains("recommended_replenishment");
    }
  }

  @Nested
  @DisplayName("materializeIncremental")
  class Incremental {

    @Test
    @DisplayName("should delegate to full materialization via fullMaterializeWithSwap")
    void should_delegateToFull_when_incrementalRun() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeIncremental(99L);

      verify(jdbc).fullMaterializeWithSwap(eq("mart_inventory_analysis"), any());
    }
  }
}
