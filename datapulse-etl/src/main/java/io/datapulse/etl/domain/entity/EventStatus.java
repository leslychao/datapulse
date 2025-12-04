package io.datapulse.etl.domain.entity;

public enum EventStatus {
  RECEIVED,
  IN_PROGRESS,
  MATERIALIZATION_PENDING,
  COMPLETED,
  FAILED,
  CANCELLED
}
