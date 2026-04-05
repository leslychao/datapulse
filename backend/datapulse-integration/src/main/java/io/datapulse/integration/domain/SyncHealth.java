package io.datapulse.integration.domain;

/** Aggregated marketplace sync health for status bar / dashboard (matches frontend {@code SyncHealth}). */
public enum SyncHealth {
  OK,
  SYNCING,
  STALE,
  ERROR
}
