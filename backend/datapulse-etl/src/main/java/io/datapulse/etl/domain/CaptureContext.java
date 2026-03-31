package io.datapulse.etl.domain;

public record CaptureContext(
        long jobExecutionId,
        long connectionId,
        EtlEventType etlEvent,
        String sourceId,
        String requestId
) {}
