package io.datapulse.etl.dto;

public enum IngestStatus {
  SUCCESS,
  PARTIAL_SUCCESS,
  FAILED,
  WAITING_RETRY,
  NO_DATA
}
