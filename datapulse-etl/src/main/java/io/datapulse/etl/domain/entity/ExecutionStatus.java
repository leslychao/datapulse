package io.datapulse.etl.domain.entity;

public enum ExecutionStatus {
  PENDING,
  RUNNING,
  WAITING_RETRY,
  MATERIALIZING,
  COMPLETED,
  FAILED
}
