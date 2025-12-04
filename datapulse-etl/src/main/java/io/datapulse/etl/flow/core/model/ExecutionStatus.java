package io.datapulse.etl.flow.core.model;

/**
 * Status of a single execution (account + source + event).
 */
public enum ExecutionStatus {
  PENDING,
  IN_PROGRESS,
  WAITING,
  SUCCESS,
  NO_DATA,
  ERROR
}
