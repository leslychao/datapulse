package io.datapulse.etl.domain;

public enum JobExecutionStatus {
    PENDING,
    IN_PROGRESS,
    MATERIALIZING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    RETRY_SCHEDULED,
    FAILED,
    STALE
}
