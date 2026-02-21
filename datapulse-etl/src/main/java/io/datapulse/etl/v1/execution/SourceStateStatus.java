package io.datapulse.etl.v1.execution;

public enum SourceStateStatus {
  NEW,
  IN_PROGRESS,
  RETRY_SCHEDULED,
  COMPLETED,
  FAILED_TERMINAL;

  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED_TERMINAL;
  }
}
