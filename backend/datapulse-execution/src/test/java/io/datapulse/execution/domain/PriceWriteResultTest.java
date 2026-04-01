package io.datapulse.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PriceWriteResult")
class PriceWriteResultTest {

  @Nested
  @DisplayName("confirmed")
  class Confirmed {

    @Test
    @DisplayName("should create CONFIRMED result with request/response summaries")
    void should_createConfirmedResult() {
      PriceWriteResult result = PriceWriteResult.confirmed("{req}", "{resp}");

      assertThat(result.outcome()).isEqualTo(PriceWriteResult.WriteOutcome.CONFIRMED);
      assertThat(result.providerRequestSummary()).isEqualTo("{req}");
      assertThat(result.providerResponseSummary()).isEqualTo("{resp}");
      assertThat(result.errorCode()).isNull();
      assertThat(result.errorMessage()).isNull();
    }
  }

  @Nested
  @DisplayName("uncertain")
  class Uncertain {

    @Test
    @DisplayName("should create UNCERTAIN result without error details")
    void should_createUncertainResult() {
      PriceWriteResult result = PriceWriteResult.uncertain("{req}", "{resp}");

      assertThat(result.outcome()).isEqualTo(PriceWriteResult.WriteOutcome.UNCERTAIN);
      assertThat(result.errorCode()).isNull();
      assertThat(result.errorMessage()).isNull();
    }
  }

  @Nested
  @DisplayName("rejected")
  class Rejected {

    @Test
    @DisplayName("should create REJECTED result with error code and message")
    void should_createRejectedResult() {
      PriceWriteResult result = PriceWriteResult.rejected(
          "{req}", "{resp}", "WB_UPLOAD_ERROR", "Invalid price");

      assertThat(result.outcome()).isEqualTo(PriceWriteResult.WriteOutcome.REJECTED);
      assertThat(result.errorCode()).isEqualTo("WB_UPLOAD_ERROR");
      assertThat(result.errorMessage()).isEqualTo("Invalid price");
      assertThat(result.providerRequestSummary()).isEqualTo("{req}");
      assertThat(result.providerResponseSummary()).isEqualTo("{resp}");
    }
  }
}
