package io.datapulse.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@DisplayName("ErrorClassifier")
class ErrorClassifierTest {

  private final ErrorClassifier classifier = new ErrorClassifier();

  @Nested
  @DisplayName("classify — connection errors")
  class ConnectionErrors {

    @Test
    @DisplayName("should classify ConnectException as RETRIABLE_TRANSIENT")
    void should_classifyRetriableTransient_when_connectException() {
      var result = classifier.classify(new ConnectException("Connection refused"));

      assertThat(result.classification()).isEqualTo(ErrorClassification.RETRIABLE_TRANSIENT);
      assertThat(result.outcome()).isEqualTo(AttemptOutcome.RETRIABLE_FAILURE);
      assertThat(result.isRetryable()).isTrue();
      assertThat(result.isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("classify — read timeout")
  class ReadTimeout {

    @Test
    @DisplayName("should classify SocketTimeoutException with 'Read timed out' as UNCERTAIN_TIMEOUT")
    void should_classifyUncertain_when_readTimedOut() {
      var result = classifier.classify(new SocketTimeoutException("Read timed out"));

      assertThat(result.classification()).isEqualTo(ErrorClassification.UNCERTAIN_TIMEOUT);
      assertThat(result.outcome()).isEqualTo(AttemptOutcome.UNCERTAIN);
      assertThat(result.isUncertain()).isTrue();
    }

    @Test
    @DisplayName("should classify wrapped read timeout in cause chain as UNCERTAIN_TIMEOUT")
    void should_classifyUncertain_when_readTimeoutInCauseChain() {
      var wrapper = new RuntimeException("Wrapper",
          new SocketTimeoutException("Read timed out"));

      var result = classifier.classify(wrapper);

      assertThat(result.classification()).isEqualTo(ErrorClassification.UNCERTAIN_TIMEOUT);
      assertThat(result.isUncertain()).isTrue();
    }

    @Test
    @DisplayName("should not classify SocketTimeoutException without 'read' as read timeout")
    void should_notClassifyAsReadTimeout_when_connectTimeout() {
      var result = classifier.classify(new SocketTimeoutException("connect timed out"));

      assertThat(result.classification()).isEqualTo(ErrorClassification.NON_RETRIABLE);
    }
  }

  @Nested
  @DisplayName("classify — HTTP errors")
  class HttpErrors {

    @Test
    @DisplayName("should classify HTTP 429 as RETRIABLE_RATE_LIMIT")
    void should_classifyRateLimit_when_http429() {
      var result = classifier.classify(httpError(429));

      assertThat(result.classification()).isEqualTo(ErrorClassification.RETRIABLE_RATE_LIMIT);
      assertThat(result.outcome()).isEqualTo(AttemptOutcome.RETRIABLE_FAILURE);
      assertThat(result.isRetryable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {502, 503, 504})
    @DisplayName("should classify HTTP 502/503/504 as RETRIABLE_TRANSIENT")
    void should_classifyRetriableTransient_when_serverError(int statusCode) {
      var result = classifier.classify(httpError(statusCode));

      assertThat(result.classification()).isEqualTo(ErrorClassification.RETRIABLE_TRANSIENT);
      assertThat(result.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("should classify HTTP 500 as RETRIABLE_TRANSIENT (server error >= 500)")
    void should_classifyRetriableTransient_when_http500() {
      var result = classifier.classify(httpError(500));

      assertThat(result.classification()).isEqualTo(ErrorClassification.RETRIABLE_TRANSIENT);
      assertThat(result.isRetryable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 405, 422})
    @DisplayName("should classify HTTP 4xx client errors as NON_RETRIABLE")
    void should_classifyNonRetriable_when_clientError(int statusCode) {
      var result = classifier.classify(httpError(statusCode));

      assertThat(result.classification()).isEqualTo(ErrorClassification.NON_RETRIABLE);
      assertThat(result.outcome()).isEqualTo(AttemptOutcome.NON_RETRIABLE_FAILURE);
      assertThat(result.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("should classify unknown HTTP 5xx as RETRIABLE_TRANSIENT")
    void should_classifyRetriableTransient_when_unknownServerError() {
      var result = classifier.classify(httpError(599));

      assertThat(result.classification()).isEqualTo(ErrorClassification.RETRIABLE_TRANSIENT);
    }

    @Test
    @DisplayName("should classify unknown HTTP 4xx as NON_RETRIABLE")
    void should_classifyNonRetriable_when_unknownClientError() {
      var result = classifier.classify(httpError(418));

      assertThat(result.classification()).isEqualTo(ErrorClassification.NON_RETRIABLE);
    }
  }

  @Nested
  @DisplayName("classify — TimeoutException")
  class TimeoutErrors {

    @Test
    @DisplayName("should classify TimeoutException as UNCERTAIN_TIMEOUT")
    void should_classifyUncertain_when_timeoutException() {
      var result = classifier.classify(new TimeoutException("Request timeout"));

      assertThat(result.classification()).isEqualTo(ErrorClassification.UNCERTAIN_TIMEOUT);
      assertThat(result.isUncertain()).isTrue();
    }
  }

  @Nested
  @DisplayName("classify — unexpected errors")
  class UnexpectedErrors {

    @Test
    @DisplayName("should classify unexpected exception as NON_RETRIABLE")
    void should_classifyNonRetriable_when_unexpectedError() {
      var result = classifier.classify(new NullPointerException("Oops"));

      assertThat(result.classification()).isEqualTo(ErrorClassification.NON_RETRIABLE);
      assertThat(result.isTerminal()).isTrue();
      assertThat(result.message()).contains("NullPointerException");
    }

    @Test
    @DisplayName("should classify IllegalStateException as NON_RETRIABLE")
    void should_classifyNonRetriable_when_illegalState() {
      var result = classifier.classify(new IllegalStateException("Invalid state"));

      assertThat(result.classification()).isEqualTo(ErrorClassification.NON_RETRIABLE);
    }
  }

  @Nested
  @DisplayName("classifyOzonItemError")
  class OzonItemErrors {

    @ParameterizedTest
    @ValueSource(strings = {"PRODUCT_NOT_FOUND", "INVALID_ARGUMENT",
        "INVALID_ATTRIBUTE", "UNKNOWN_ATTRIBUTE"})
    @DisplayName("should classify known Ozon error codes as NON_RETRIABLE")
    void should_classifyNonRetriable_when_knownOzonErrorCode(String errorCode) {
      var result = classifier.classifyOzonItemError(errorCode, "Some message");

      assertThat(result.classification()).isEqualTo(ErrorClassification.NON_RETRIABLE);
      assertThat(result.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("should classify Ozon rate-limit code as RETRIABLE_RATE_LIMIT")
    void should_classifyRateLimit_when_ozonRateLimitCode() {
      var result = classifier.classifyOzonItemError("RATE_LIMIT_EXCEEDED", "Too many requests");

      assertThat(result.classification()).isEqualTo(ErrorClassification.RETRIABLE_RATE_LIMIT);
      assertThat(result.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("should classify unknown Ozon error code as NON_RETRIABLE (safe default)")
    void should_classifyNonRetriable_when_unknownOzonErrorCode() {
      var result = classifier.classifyOzonItemError("SOME_NEW_CODE", "Unexpected");

      assertThat(result.classification()).isEqualTo(ErrorClassification.NON_RETRIABLE);
      assertThat(result.isTerminal()).isTrue();
    }
  }

  @Nested
  @DisplayName("ErrorClassificationResult")
  class ResultContract {

    @Test
    @DisplayName("isRetryable should be true only for RETRIABLE_FAILURE outcome")
    void should_reportRetryable_when_outcomeIsRetriableFailure() {
      var result = new ErrorClassifier.ErrorClassificationResult(
          ErrorClassification.RETRIABLE_TRANSIENT,
          AttemptOutcome.RETRIABLE_FAILURE, "test");

      assertThat(result.isRetryable()).isTrue();
      assertThat(result.isUncertain()).isFalse();
      assertThat(result.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("isUncertain should be true only for UNCERTAIN outcome")
    void should_reportUncertain_when_outcomeIsUncertain() {
      var result = new ErrorClassifier.ErrorClassificationResult(
          ErrorClassification.UNCERTAIN_TIMEOUT,
          AttemptOutcome.UNCERTAIN, "test");

      assertThat(result.isUncertain()).isTrue();
      assertThat(result.isRetryable()).isFalse();
      assertThat(result.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("isTerminal should be true only for NON_RETRIABLE_FAILURE outcome")
    void should_reportTerminal_when_outcomeIsNonRetriable() {
      var result = new ErrorClassifier.ErrorClassificationResult(
          ErrorClassification.NON_RETRIABLE,
          AttemptOutcome.NON_RETRIABLE_FAILURE, "test");

      assertThat(result.isTerminal()).isTrue();
      assertThat(result.isRetryable()).isFalse();
      assertThat(result.isUncertain()).isFalse();
    }
  }

  private WebClientResponseException httpError(int statusCode) {
    return WebClientResponseException.create(
        statusCode, "Error " + statusCode,
        HttpHeaders.EMPTY, new byte[0], null);
  }
}
