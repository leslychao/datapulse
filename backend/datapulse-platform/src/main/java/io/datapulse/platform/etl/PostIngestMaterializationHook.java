package io.datapulse.platform.etl;

/**
 * Invoked by the ingest pipeline after ingest data is committed and the job is in
 * {@code MATERIALIZING}. Implementations refresh ClickHouse / mart tables from data written
 * during the same ingest run.
 */
@FunctionalInterface
public interface PostIngestMaterializationHook {

  PostIngestMaterializationResult afterSuccessfulIngest(long jobExecutionId);
}
