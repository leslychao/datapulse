package io.datapulse.etl.flow;

public enum EtlOutcome {
  SKIP,
  SUCCESS,
  TERMINAL_FAIL,
  WAIT
}
