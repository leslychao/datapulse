package io.datapulse.pricing.domain;

public enum RunStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    PAUSED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPLETED_WITH_ERRORS
                || this == FAILED || this == CANCELLED;
    }

    public boolean isResumable() {
        return this == PAUSED;
    }
}
