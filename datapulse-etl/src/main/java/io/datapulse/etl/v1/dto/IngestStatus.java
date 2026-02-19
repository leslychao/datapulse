package io.datapulse.etl.v1.dto;

public enum IngestStatus {
  SUCCESS,
  FAILED,
  WAITING_RETRY,
  NO_DATA;

  public boolean isTerminal() {
    return this != WAITING_RETRY;
  }
}
