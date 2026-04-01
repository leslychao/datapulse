package io.datapulse.sellerops.domain;

public enum QueueAssignmentStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    DISMISSED;

    public boolean isTerminal() {
        return this == DONE || this == DISMISSED;
    }
}
