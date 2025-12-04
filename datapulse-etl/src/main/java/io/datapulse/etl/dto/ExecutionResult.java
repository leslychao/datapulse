package io.datapulse.etl.dto;

/**
 * Carries the outcome of a single execution attempt together with its command metadata.
 */
public record ExecutionResult(
    EtlSourceExecution execution,
    IngestResult ingestResult
) {
}
