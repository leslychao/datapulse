package io.datapulse.platform.etl;

/**
 * Invoked by the ingest pipeline after a sync job finishes successfully (including
 * {@code COMPLETED_WITH_ERRORS}). Implementations refresh ClickHouse mart tables from facts
 * written during the same ingest run.
 */
@FunctionalInterface
public interface PostIngestMaterializationHook {

  void afterSuccessfulIngest(long jobExecutionId);
}
