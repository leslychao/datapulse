package io.datapulse.execution.domain;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Classifies marketplace API errors into categories that determine
 * the action lifecycle transition (retry vs fail vs reconciliation).
 *
 * Classification hierarchy (from execution.md):
 * - Connect timeout (request never sent) → RETRIABLE_TRANSIENT → retry
 * - Read timeout (request sent, no response) → UNCERTAIN_TIMEOUT → reconciliation
 * - HTTP 429 → RETRIABLE_RATE_LIMIT → retry with backoff
 * - HTTP 503, 502, 504 → RETRIABLE_TRANSIENT → retry
 * - HTTP 400, validation → NON_RETRIABLE → fail immediately
 * - Ozon per-item errors → classified by error code
 */
@Component
public class ErrorClassifier {

    private static final Set<Integer> RETRIABLE_HTTP_CODES = Set.of(429, 502, 503, 504);
    private static final Set<Integer> NON_RETRIABLE_HTTP_CODES = Set.of(400, 401, 403, 404, 405, 422);

    private static final Set<String> OZON_NON_RETRIABLE_CODES = Set.of(
            "PRODUCT_NOT_FOUND",
            "INVALID_ARGUMENT",
            "INVALID_ATTRIBUTE",
            "UNKNOWN_ATTRIBUTE"
    );

    public ErrorClassificationResult classify(Throwable error) {
        if (error instanceof ConnectException) {
            return result(ErrorClassification.RETRIABLE_TRANSIENT,
                    AttemptOutcome.RETRIABLE_FAILURE,
                    "Connect timeout: request not sent to provider");
        }

        if (isReadTimeout(error)) {
            return result(ErrorClassification.UNCERTAIN_TIMEOUT,
                    AttemptOutcome.UNCERTAIN,
                    "Read timeout: request sent, response unknown");
        }

        if (error instanceof WebClientResponseException httpError) {
            return classifyHttpError(httpError);
        }

        if (error instanceof TimeoutException) {
            return result(ErrorClassification.UNCERTAIN_TIMEOUT,
                    AttemptOutcome.UNCERTAIN,
                    "Timeout: " + error.getMessage());
        }

        return result(ErrorClassification.NON_RETRIABLE,
                AttemptOutcome.NON_RETRIABLE_FAILURE,
                "Unexpected error: " + error.getClass().getSimpleName() + " - " + error.getMessage());
    }

    public ErrorClassificationResult classifyOzonItemError(String errorCode, String errorMessage) {
        if (OZON_NON_RETRIABLE_CODES.contains(errorCode)) {
            return result(ErrorClassification.NON_RETRIABLE,
                    AttemptOutcome.NON_RETRIABLE_FAILURE,
                    "Ozon item error: " + errorCode + " - " + errorMessage);
        }

        if (isOzonRateLimitCode(errorCode)) {
            return result(ErrorClassification.RETRIABLE_RATE_LIMIT,
                    AttemptOutcome.RETRIABLE_FAILURE,
                    "Ozon per-product rate limit: " + errorCode);
        }

        return result(ErrorClassification.NON_RETRIABLE,
                AttemptOutcome.NON_RETRIABLE_FAILURE,
                "Ozon unknown error code (safe default=fail): " + errorCode + " - " + errorMessage);
    }

    private ErrorClassificationResult classifyHttpError(WebClientResponseException error) {
        int statusCode = error.getStatusCode().value();

        if (statusCode == 429) {
            return result(ErrorClassification.RETRIABLE_RATE_LIMIT,
                    AttemptOutcome.RETRIABLE_FAILURE,
                    "HTTP 429: rate limited");
        }

        if (RETRIABLE_HTTP_CODES.contains(statusCode)) {
            return result(ErrorClassification.RETRIABLE_TRANSIENT,
                    AttemptOutcome.RETRIABLE_FAILURE,
                    "HTTP " + statusCode + ": transient server error");
        }

        if (NON_RETRIABLE_HTTP_CODES.contains(statusCode)) {
            return result(ErrorClassification.NON_RETRIABLE,
                    AttemptOutcome.NON_RETRIABLE_FAILURE,
                    "HTTP " + statusCode + ": " + error.getMessage());
        }

        if (statusCode >= 500) {
            return result(ErrorClassification.RETRIABLE_TRANSIENT,
                    AttemptOutcome.RETRIABLE_FAILURE,
                    "HTTP " + statusCode + ": server error (assumed transient)");
        }

        return result(ErrorClassification.NON_RETRIABLE,
                AttemptOutcome.NON_RETRIABLE_FAILURE,
                "HTTP " + statusCode + ": " + error.getMessage());
    }

    private boolean isReadTimeout(Throwable error) {
        if (error instanceof SocketTimeoutException ste) {
            String msg = ste.getMessage();
            return msg != null && msg.toLowerCase().contains("read timed out");
        }
        Throwable cause = error.getCause();
        return cause != null && cause != error && isReadTimeout(cause);
    }

    private boolean isOzonRateLimitCode(String errorCode) {
        return errorCode != null && errorCode.toLowerCase().contains("rate");
    }

    private ErrorClassificationResult result(ErrorClassification classification,
                                             AttemptOutcome outcome,
                                             String message) {
        return new ErrorClassificationResult(classification, outcome, message);
    }

    public record ErrorClassificationResult(
            ErrorClassification classification,
            AttemptOutcome outcome,
            String message
    ) {

        public boolean isRetryable() {
            return outcome == AttemptOutcome.RETRIABLE_FAILURE;
        }

        public boolean isUncertain() {
            return outcome == AttemptOutcome.UNCERTAIN;
        }

        public boolean isTerminal() {
            return outcome == AttemptOutcome.NON_RETRIABLE_FAILURE;
        }
    }
}
