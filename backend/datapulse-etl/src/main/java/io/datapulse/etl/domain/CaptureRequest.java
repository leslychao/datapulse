package io.datapulse.etl.domain;

import java.io.InputStream;

public record CaptureRequest(
        long jobExecutionId,
        long connectionId,
        EtlEventType etlEvent,
        String sourceId,
        String requestId,
        int pageNumber,
        InputStream responseBody
) {}
