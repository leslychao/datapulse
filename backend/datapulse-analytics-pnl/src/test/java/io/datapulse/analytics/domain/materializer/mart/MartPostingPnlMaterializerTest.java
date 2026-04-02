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
@DisplayName("MartPostingPnlMaterializer")
class MartPostingPnlMaterializerTest {

  @Mock private MaterializationJdbc jdbc;
  @Mock private JdbcTemplate chTemplate;

  @InjectMocks
  private MartPostingPnlMaterializer materializer;

  @Test
  @DisplayName("should report table name as 'mart_posting_pnl'")
  void should_returnTableName() {
    assertThat(materializer.tableName()).isEqualTo("mart_posting_pnl");
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
    @DisplayName("should use fullMaterializeWithSwap for mart_posting_pnl")
    void should_useSwap_when_fullRun() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeFull();

      verify(jdbc).fullMaterializeWithSwap(eq("mart_posting_pnl"), any());
    }

    @Test
    @DisplayName("should execute INSERT SQL with ver placeholder filled")
    void should_executeInsertSql_when_fullRun() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(100L);
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeFull();

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate, atLeast(1)).execute(sqlCaptor.capture());

      String executedSql = sqlCaptor.getAllValues().stream()
          .filter(s -> s.contains("INSERT INTO"))
          .findFirst()
          .orElse(null);
      assertThat(executedSql).isNotNull();
      assertThat(executedSql).doesNotContain("%d");
    }

    @Test
    @DisplayName("should query count after materialization")
    void should_queryCount_when_fullCompletes() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject("SELECT count() FROM mart_posting_pnl", Long.class))
          .thenReturn(5000L);
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeFull();

      verify(chTemplate).queryForObject("SELECT count() FROM mart_posting_pnl", Long.class);
    }

    @Test
    @DisplayName("SQL should contain P&L formula: revenue + all cost components")
    void should_containPnlFormula_when_sqlGenerated() {
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

      assertThat(sql).contains("revenue_amount");
      assertThat(sql).contains("marketplace_commission_amount");
      assertThat(sql).contains("acquiring_commission_amount");
      assertThat(sql).contains("logistics_cost_amount");
      assertThat(sql).contains("storage_cost_amount");
      assertThat(sql).contains("penalties_amount");
      assertThat(sql).contains("marketing_cost_amount");
      assertThat(sql).contains("other_marketplace_charges_amount");
      assertThat(sql).contains("compensation_amount");
      assertThat(sql).contains("refund_amount");
    }

    @Test
    @DisplayName("SQL should contain COGS calculation with division-by-zero guard")
    void should_containCogsWithDivisionGuard_when_sqlGenerated() {
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

      assertThat(sql).contains("gross_cogs");
      assertThat(sql).contains("net_cogs");
      assertThat(sql).contains("nullIf(orv.order_revenue, 0)");
      assertThat(sql).contains("cogs_status");
    }

    @Test
    @DisplayName("SQL should contain acquiring pro-rata allocation")
    void should_containAcquiringAllocation_when_sqlGenerated() {
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

      assertThat(sql).contains("order_acquiring");
      assertThat(sql).contains("pm.revenue_amount / nullIf(orv.order_revenue, 0)");
    }

    @Test
    @DisplayName("SQL should contain reconciliation residual calculation")
    void should_containReconciliationResidual_when_sqlGenerated() {
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

      assertThat(sql).contains("reconciliation_residual");
    }
  }

  @Nested
  @DisplayName("materializeIncremental")
  class Incremental {

    @Test
    @DisplayName("should filter by affected postings from job execution")
    void should_filterByAffectedPostings_when_incrementalRun() {
      when(jdbc.ch()).thenReturn(chTemplate);

      materializer.materializeIncremental(42L);

      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(chTemplate).execute(sqlCaptor.capture());

      String sql = sqlCaptor.getValue();
      assertThat(sql).contains("job_execution_id = 42");
    }
  }
}
