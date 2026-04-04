package io.datapulse.etl.config;

/**
 * How ClickHouse / mart materialization runs after a successful DAG ingest.
 *
 * <p>{@link #SYNC} — coordinator calls {@link io.datapulse.platform.etl.PostIngestMaterializationHook}
 * inline (default). {@link #ASYNC_OUTBOX} — enqueue {@code ETL_POST_INGEST_MATERIALIZE} so work
 * happens on the {@code etl.sync} consumer, keeping the ingest worker responsive.
 */
public enum PostIngestMaterializationMode {
  SYNC,
  ASYNC_OUTBOX
}
