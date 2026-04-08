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
@DisplayName("MartReturnsAnalysisMaterializer")
class MartReturnsAnalysisMaterializerTest {

  @Mock private MaterializationJdbc jdbc;
  @Mock private JdbcTemplate chTemplate;

  @InjectMocks
  private MartReturnsAnalysisMaterializer materializer;

  @Test
  @DisplayName("should report table name as 'mart_returns_analysis'")
  void should_returnTableName() {
    assertThat(materializer.tableName()).isEqualTo("mart_returns_analysis");
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
    @DisplayName("should use fullMaterializeWithSwap and execute insert with return rate")
    void should_useSwapAndInsert_when_fullRun() {
      when(jdbc.ch()).thenReturn(chTemplate);
      when(chTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(50L);
      doAnswer(invocation -> {
        Consumer<String> populate = invocation.getArgument(1);
        populate.accept(invocation.getArgument(0) + "_staging");
        return null;
      }).when(jdbc).fullMaterializeWithSwap(anyString(), any());

      materializer.materializeFull();

      verify(jdbc).fullMaterializeWithSwap(eq("mart_returns_analysis"), any());
    }

    @Test
    @DisplayName("SQL should contain return_rate_pct calculation with zero-division guard")
    void should_containReturnRate_when_sqlGenerated() {
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

      assertThat(sql).contains("return_rate_pct");
      assertThat(sql).contains("s.sale_quantity > 0");
    }

    @Test
    @DisplayName("SQL should join fact_returns with fact_sales (no fact_finance)")
    void should_joinTwoFactTables_when_sqlGenerated() {
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

      assertThat(sql).contains("fact_returns");
      assertThat(sql).contains("fact_sales");
      assertThat(sql).doesNotContain("fact_finance");
    }

    @Test
    @DisplayName("SQL should extract top return reason")
    void should_extractTopReason_when_sqlGenerated() {
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

      assertThat(sql).contains("top_return_reason");
      assertThat(sql).contains("topK");
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

      verify(jdbc).fullMaterializeWithSwap(eq("mart_returns_analysis"), any());
    }
  }
}
