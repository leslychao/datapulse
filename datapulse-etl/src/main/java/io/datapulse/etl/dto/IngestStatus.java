package io.datapulse.etl.dto;

public enum IngestStatus {
  SUCCESS,
  FAILED,
  WAITING_RETRY,
  NO_DATA
}
