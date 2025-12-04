package io.datapulse.etl.nextgen.dto;

public enum EventStatus {
  PENDING,
  IN_PROGRESS,
  SUCCESS,
  NO_DATA,
  PARTIAL_SUCCESS,
  FAILED,
  CANCELLED
}
