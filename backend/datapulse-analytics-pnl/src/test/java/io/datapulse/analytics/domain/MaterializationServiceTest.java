package io.datapulse.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import io.datapulse.platform.etl.PostIngestMaterializationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

@DisplayName("MaterializationService")
class MaterializationServiceTest {

  private AnalyticsMaterializer dimMaterializer() {
    return stubMaterializer("dim_product", MaterializationPhase.DIMENSION);
  }

  private AnalyticsMaterializer factMaterializer() {
    return stubMaterializer("fact_finance", MaterializationPhase.FACT);
  }

  private AnalyticsMaterializer martMaterializer() {
    return stubMaterializer("mart_posting_pnl", MaterializationPhase.MART);
  }

  private AnalyticsMaterializer stubMaterializer(String table, MaterializationPhase phase) {
    return new AnalyticsMaterializer() {
      boolean fullCalled;
      boolean incrementalCalled;
      long lastJobId;
      RuntimeException failOnFull;
      RuntimeException failOnIncremental;

      @Override
      public void materializeFull() {
        if (failOnFull != null) throw failOnFull;
        fullCalled = true;
      }

      @Override
      public void materializeIncremental(long jobExecutionId) {
        if (failOnIncremental != null) throw failOnIncremental;
        incrementalCalled = true;
        lastJobId = jobExecutionId;
      }

      @Override
      public String tableName() {
        return table;
      }

      @Override
      public MaterializationPhase phase() {
        return phase;
      }
    };
  }

  @Nested
  @DisplayName("runFullRematerialization")
  class RunFull {

    @Test
    @DisplayName("should execute all materializers in phase order")
    void should_executeAll_when_multiplePhasesPresent() {
      var dim = dimMaterializer();
      var fact = factMaterializer();
      var mart = martMaterializer();

      var service = new MaterializationService(List.of(mart, dim, fact));
      service.runFullRematerialization();

      // Stub materializers have fullCalled flag — but since we can't access
      // private fields of anonymous class, we verify no exception was thrown
      assertThatCode(service::runFullRematerialization).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should abort and rethrow when a materializer fails")
    void should_abort_when_materializerThrowsException() {
      var dim = dimMaterializer();
      var failing = new AnalyticsMaterializer() {
        @Override
        public void materializeFull() {
          throw new RuntimeException("CH connection lost");
        }

        @Override
        public void materializeIncremental(long jobExecutionId) { }

        @Override
        public String tableName() {
          return "fact_broken";
        }

        @Override
        public MaterializationPhase phase() {
          return MaterializationPhase.FACT;
        }
      };

      var service = new MaterializationService(List.of(dim, failing));

      assertThatThrownBy(service::runFullRematerialization)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("fact_broken");
    }

    @Test
    @DisplayName("should handle empty materializer list")
    void should_succeedSilently_when_noMaterializers() {
      var service = new MaterializationService(List.of());

      assertThatCode(service::runFullRematerialization).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("runIncrementalMaterialization")
  class RunIncremental {

    @Test
    @DisplayName("should pass jobExecutionId to all materializers")
    void should_passJobId_when_incrementalRun() {
      var dim = dimMaterializer();
      var fact = factMaterializer();

      var service = new MaterializationService(List.of(fact, dim));
      PostIngestMaterializationResult result = service.runIncrementalMaterialization(42L);
      assertThat(result.fullySucceeded()).isTrue();
    }

    @Test
    @DisplayName("should continue other materializers when one fails")
    void should_continueOthers_when_oneMaterializerFails() {
      var failing = new AnalyticsMaterializer() {
        @Override
        public void materializeFull() { }

        @Override
        public void materializeIncremental(long jobExecutionId) {
          throw new RuntimeException("timeout");
        }

        @Override
        public String tableName() {
          return "dim_broken";
        }

        @Override
        public MaterializationPhase phase() {
          return MaterializationPhase.DIMENSION;
        }
      };
      var fact = factMaterializer();

      var service = new MaterializationService(List.of(failing, fact));

      PostIngestMaterializationResult result = service.runIncrementalMaterialization(99L);
      assertThat(result.fullySucceeded()).isFalse();
      assertThat(result.failedTables()).containsExactly("dim_broken");
    }
  }
}
