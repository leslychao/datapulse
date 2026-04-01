package io.datapulse.execution.domain.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.datapulse.execution.domain.AttemptOutcome;
import io.datapulse.execution.domain.ErrorClassification;
import io.datapulse.execution.domain.ReconciliationSource;

@DisplayName("GatewayResult")
class GatewayResultTest {

  @Nested
  @DisplayName("confirmed")
  class Confirmed {

    @Test
    @DisplayName("should create SUCCESS result with IMMEDIATE reconciliation")
    void should_createSuccessResult_when_confirmed() {
      GatewayResult result = GatewayResult.confirmed("{req}", "{resp}");

      assertThat(result.outcome()).isEqualTo(AttemptOutcome.SUCCESS);
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.isUncertain()).isFalse();
      assertThat(result.isRetriable()).isFalse();
      assertThat(result.errorClassification()).isNull();
      assertThat(result.errorMessage()).isNull();
      assertThat(result.reconciliationSource()).isEqualTo(ReconciliationSource.IMMEDIATE);
      assertThat(result.priceMatch()).isTrue();
      assertThat(result.providerRequestSummary()).isEqualTo("{req}");
      assertThat(result.providerResponseSummary()).isEqualTo("{resp}");
    }
  }

  @Nested
  @DisplayName("uncertain")
  class Uncertain {

    @Test
    @DisplayName("should create UNCERTAIN result with UNCERTAIN_TIMEOUT classification")
    void should_createUncertainResult_when_uncertain() {
      GatewayResult result = GatewayResult.uncertain("{req}", "{resp}");

      assertThat(result.outcome()).isEqualTo(AttemptOutcome.UNCERTAIN);
      assertThat(result.isUncertain()).isTrue();
      assertThat(result.isSuccess()).isFalse();
      assertThat(result.isRetriable()).isFalse();
      assertThat(result.errorClassification()).isEqualTo(ErrorClassification.UNCERTAIN_TIMEOUT);
      assertThat(result.reconciliationSource()).isNull();
    }
  }

  @Nested
  @DisplayName("retriable")
  class Retriable {

    @Test
    @DisplayName("should create RETRIABLE_FAILURE result")
    void should_createRetriableResult_when_retriable() {
      GatewayResult result = GatewayResult.retriable(
          ErrorClassification.RETRIABLE_TRANSIENT, "Server error",
          "{req}", "{resp}");

      assertThat(result.outcome()).isEqualTo(AttemptOutcome.RETRIABLE_FAILURE);
      assertThat(result.isRetriable()).isTrue();
      assertThat(result.isSuccess()).isFalse();
      assertThat(result.isUncertain()).isFalse();
      assertThat(result.errorClassification())
          .isEqualTo(ErrorClassification.RETRIABLE_TRANSIENT);
      assertThat(result.errorMessage()).isEqualTo("Server error");
      assertThat(result.reconciliationSource()).isNull();
    }
  }

  @Nested
  @DisplayName("terminal")
  class Terminal {

    @Test
    @DisplayName("should create NON_RETRIABLE_FAILURE result")
    void should_createTerminalResult_when_terminal() {
      GatewayResult result = GatewayResult.terminal(
          ErrorClassification.NON_RETRIABLE, "Bad request",
          "{req}", "{resp}");

      assertThat(result.outcome()).isEqualTo(AttemptOutcome.NON_RETRIABLE_FAILURE);
      assertThat(result.isSuccess()).isFalse();
      assertThat(result.isUncertain()).isFalse();
      assertThat(result.isRetriable()).isFalse();
      assertThat(result.errorClassification()).isEqualTo(ErrorClassification.NON_RETRIABLE);
      assertThat(result.errorMessage()).isEqualTo("Bad request");
      assertThat(result.reconciliationSource()).isNull();
    }
  }
}
