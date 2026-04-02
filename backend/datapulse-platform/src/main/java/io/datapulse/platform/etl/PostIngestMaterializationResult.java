package io.datapulse.platform.etl;

import java.util.List;

/**
 * Outcome of incremental ClickHouse / mart refresh after a successful ingest phase.
 */
public record PostIngestMaterializationResult(
    boolean fullySucceeded, List<String> failedTables, String fatalError) {

  public static PostIngestMaterializationResult ok() {
    return new PostIngestMaterializationResult(true, List.of(), null);
  }

  public static PostIngestMaterializationResult partialFailure(List<String> failedTables) {
    return new PostIngestMaterializationResult(false, List.copyOf(failedTables), null);
  }

  public static PostIngestMaterializationResult fatal(String message) {
    return new PostIngestMaterializationResult(false, List.of(), message);
  }
}
