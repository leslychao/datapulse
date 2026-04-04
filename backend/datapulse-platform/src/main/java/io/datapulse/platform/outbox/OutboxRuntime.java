package io.datapulse.platform.outbox;

import java.util.EnumSet;
import java.util.Set;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OutboxRuntime {

    INGEST(EnumSet.of(
            OutboxEventType.ETL_SYNC_EXECUTE,
            OutboxEventType.ETL_SYNC_RETRY,
            OutboxEventType.ETL_POST_INGEST_MATERIALIZE,
            OutboxEventType.ETL_SYNC_COMPLETED,
            OutboxEventType.ETL_PROMO_CAMPAIGN_STALE,
            OutboxEventType.REMATERIALIZATION_REQUESTED
    )),

    PRICING(EnumSet.of(
            OutboxEventType.PRICING_RUN_EXECUTE,
            OutboxEventType.PROMO_EVALUATION_EXECUTE
    )),

    EXECUTOR(EnumSet.of(
            OutboxEventType.PRICE_ACTION_EXECUTE,
            OutboxEventType.PRICE_ACTION_RETRY,
            OutboxEventType.RECONCILIATION_CHECK,
            OutboxEventType.PROMO_ACTION_EXECUTE
    )),

    ALL(EnumSet.allOf(OutboxEventType.class));

    private final Set<OutboxEventType> eventTypes;
}
