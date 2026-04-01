package io.datapulse.promotions.domain;

public enum PromoActionStatus {
    PENDING_APPROVAL,
    APPROVED,
    EXECUTING,
    SUCCEEDED,
    FAILED,
    EXPIRED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == EXPIRED || this == CANCELLED;
    }

    public boolean isCancellable() {
        return this == PENDING_APPROVAL || this == APPROVED;
    }
}
