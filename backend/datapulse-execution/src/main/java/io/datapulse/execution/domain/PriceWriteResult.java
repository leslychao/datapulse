package io.datapulse.execution.domain;

public record PriceWriteResult(
        WriteOutcome outcome,
        String providerRequestSummary,
        String providerResponseSummary,
        String errorCode,
        String errorMessage
) {

    public static PriceWriteResult confirmed(String requestSummary, String responseSummary) {
        return new PriceWriteResult(WriteOutcome.CONFIRMED, requestSummary, responseSummary, null, null);
    }

    public static PriceWriteResult uncertain(String requestSummary, String responseSummary) {
        return new PriceWriteResult(WriteOutcome.UNCERTAIN, requestSummary, responseSummary, null, null);
    }

    public static PriceWriteResult rejected(String requestSummary, String responseSummary,
                                            String errorCode, String errorMessage) {
        return new PriceWriteResult(WriteOutcome.REJECTED, requestSummary, responseSummary,
                errorCode, errorMessage);
    }

    public enum WriteOutcome {
        CONFIRMED,
        UNCERTAIN,
        REJECTED
    }
}
