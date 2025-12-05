package io.datapulse.etl.flow.core.model;

/**
 * Aggregated status of an event that may span multiple executions.
 */
public enum EventStatus {
  PENDING,
  IN_PROGRESS,
  WAITING,
  SUCCESS,
  PARTIAL_SUCCESS,
  NO_DATA,
  ERROR
}
