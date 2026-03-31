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
}
