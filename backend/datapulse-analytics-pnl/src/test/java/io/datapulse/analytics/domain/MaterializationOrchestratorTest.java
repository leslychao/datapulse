package io.datapulse.analytics.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

@DisplayName("MaterializationOrchestrator")
class MaterializationOrchestratorTest {

  private AnalyticsMaterializer mockMaterializer(String table, MaterializationPhase phase) {
    var mat = mock(AnalyticsMaterializer.class);
    when(mat.tableName()).thenReturn(table);
    when(mat.phase()).thenReturn(phase);
    return mat;
  }

  @Nested
  @DisplayName("runFull")
  class RunFull {

    @Test
    @DisplayName("should execute materializers in DIMENSION → FACT → MART order")
    void should_executeInPhaseOrder_when_runFull() {
      var dim = mockMaterializer("dim_product", MaterializationPhase.DIMENSION);
      var fact = mockMaterializer("fact_finance", MaterializationPhase.FACT);
      var mart = mockMaterializer("mart_posting_pnl", MaterializationPhase.MART);

      var orchestrator = new MaterializationOrchestrator(List.of(mart, dim, fact));
      orchestrator.runFull();

      InOrder order = inOrder(dim, fact, mart);
      order.verify(dim).materializeFull();
      order.verify(fact).materializeFull();
      order.verify(mart).materializeFull();
    }

    @Test
    @DisplayName("should rethrow exception from failing materializer")
    void should_rethrow_when_materializerFails() {
      var dim = mockMaterializer("dim_product", MaterializationPhase.DIMENSION);
      var fact = mockMaterializer("fact_finance", MaterializationPhase.FACT);
      doThrow(new RuntimeException("CH down")).when(fact).materializeFull();

      var orchestrator = new MaterializationOrchestrator(List.of(dim, fact));

      assertThatThrownBy(orchestrator::runFull)
          .isInstanceOf(RuntimeException.class)
          .hasMessage("CH down");

      verify(dim).materializeFull();
    }

    @Test
    @DisplayName("should not call subsequent materializers after failure")
    void should_stopAfterFailure_when_earlyMaterializerFails() {
      var dim = mockMaterializer("dim_product", MaterializationPhase.DIMENSION);
      doThrow(new RuntimeException("fail")).when(dim).materializeFull();
      var fact = mockMaterializer("fact_finance", MaterializationPhase.FACT);

      var orchestrator = new MaterializationOrchestrator(List.of(dim, fact));

      assertThatThrownBy(orchestrator::runFull).isInstanceOf(RuntimeException.class);
      verify(fact, never()).materializeFull();
    }
  }

  @Nested
  @DisplayName("runIncremental")
  class RunIncremental {

    @Test
    @DisplayName("should pass jobExecutionId to all materializers in order")
    void should_passJobIdInOrder_when_incrementalRun() {
      var dim = mockMaterializer("dim_product", MaterializationPhase.DIMENSION);
      var mart = mockMaterializer("mart_posting_pnl", MaterializationPhase.MART);

      var orchestrator = new MaterializationOrchestrator(List.of(mart, dim));
      orchestrator.runIncremental(77L);

      InOrder order = inOrder(dim, mart);
      order.verify(dim).materializeIncremental(77L);
      order.verify(mart).materializeIncremental(77L);
    }

    @Test
    @DisplayName("should rethrow and stop on failure during incremental")
    void should_rethrowAndStop_when_incrementalFails() {
      var dim = mockMaterializer("dim_product", MaterializationPhase.DIMENSION);
      doThrow(new RuntimeException("timeout")).when(dim).materializeIncremental(5L);
      var fact = mockMaterializer("fact_finance", MaterializationPhase.FACT);

      var orchestrator = new MaterializationOrchestrator(List.of(dim, fact));

      assertThatThrownBy(() -> orchestrator.runIncremental(5L))
          .isInstanceOf(RuntimeException.class);
      verify(fact, never()).materializeIncremental(5L);
    }
  }
}
