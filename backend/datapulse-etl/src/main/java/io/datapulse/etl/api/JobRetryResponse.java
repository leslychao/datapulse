package io.datapulse.etl.api;

public record JobRetryResponse(
        long jobId,
        String message
) {
}
