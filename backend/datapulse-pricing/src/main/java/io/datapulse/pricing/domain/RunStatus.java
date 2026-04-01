package io.datapulse.pricing.domain;

public enum RunStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPLETED_WITH_ERRORS || this == FAILED;
    }
}
