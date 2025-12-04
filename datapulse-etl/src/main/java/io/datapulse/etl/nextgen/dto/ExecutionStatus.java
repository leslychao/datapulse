package io.datapulse.etl.nextgen.dto;

public enum ExecutionStatus {
  PENDING,
  IN_PROGRESS,
  WAITING_RETRY,
  SUCCESS,
  NO_DATA,
  FAILED_FINAL,
  CANCELLED
}
