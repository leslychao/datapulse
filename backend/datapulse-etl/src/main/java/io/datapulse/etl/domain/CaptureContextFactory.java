package io.datapulse.etl.domain;

import java.util.UUID;

/**
 * Builds {@link CaptureContext} from {@link IngestContext}.
 * Extracted from {@code EventRunner} so every {@link EventSource} can use it
 * without duplicating the construction logic.
 */
public final class CaptureContextFactory {

    private CaptureContextFactory() {
    }

    public static CaptureContext build(IngestContext ctx, EtlEventType event, String sourceId) {
        return new CaptureContext(
                ctx.jobExecutionId(),
                ctx.connectionId(),
                event,
                sourceId,
                UUID.randomUUID().toString().substring(0, 8)
        );
    }

    /**
     * Creates a copy with a fresh {@code requestId} — used when a single adapter
     * splits work into multiple date windows, each needing unique S3 paths.
     */
    public static CaptureContext withNewRequestId(CaptureContext ctx) {
        return new CaptureContext(
                ctx.jobExecutionId(),
                ctx.connectionId(),
                ctx.etlEvent(),
                ctx.sourceId(),
                UUID.randomUUID().toString().substring(0, 8)
        );
    }
}
