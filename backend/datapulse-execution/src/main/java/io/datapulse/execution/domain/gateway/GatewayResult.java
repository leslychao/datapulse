package io.datapulse.execution.domain.gateway;

import io.datapulse.execution.domain.AttemptOutcome;
import io.datapulse.execution.domain.ErrorClassification;
import io.datapulse.execution.domain.ReconciliationSource;

import java.math.BigDecimal;

/**
 * Result of executing a price action through the gateway.
 * Encapsulates the attempt outcome, provider evidence, and reconciliation data.
 */
public record GatewayResult(
        AttemptOutcome outcome,
        ErrorClassification errorClassification,
        String errorMessage,
        String providerRequestSummary,
        String providerResponseSummary,
        ReconciliationSource reconciliationSource,
        BigDecimal actualPrice,
        Boolean priceMatch
) {

    public static GatewayResult confirmed(String requestSummary, String responseSummary) {
        return new GatewayResult(
                AttemptOutcome.SUCCESS, null, null,
                requestSummary, responseSummary,
                ReconciliationSource.IMMEDIATE, null, true
        );
    }

    public static GatewayResult uncertain(String requestSummary, String responseSummary) {
        return new GatewayResult(
                AttemptOutcome.UNCERTAIN, ErrorClassification.UNCERTAIN_TIMEOUT, null,
                requestSummary, responseSummary,
                null, null, null
        );
    }

    public static GatewayResult retriable(ErrorClassification classification, String errorMessage,
                                          String requestSummary, String responseSummary) {
        return new GatewayResult(
                AttemptOutcome.RETRIABLE_FAILURE, classification, errorMessage,
                requestSummary, responseSummary,
                null, null, null
        );
    }

    public static GatewayResult terminal(ErrorClassification classification, String errorMessage,
                                         String requestSummary, String responseSummary) {
        return new GatewayResult(
                AttemptOutcome.NON_RETRIABLE_FAILURE, classification, errorMessage,
                requestSummary, responseSummary,
                null, null, null
        );
    }

    public boolean isSuccess() {
        return outcome == AttemptOutcome.SUCCESS;
    }

    public boolean isUncertain() {
        return outcome == AttemptOutcome.UNCERTAIN;
    }

    public boolean isRetriable() {
        return outcome == AttemptOutcome.RETRIABLE_FAILURE;
    }
}
