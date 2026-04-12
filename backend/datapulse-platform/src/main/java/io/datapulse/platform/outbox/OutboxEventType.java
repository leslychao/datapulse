package io.datapulse.platform.outbox;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OutboxEventType {

    ETL_SYNC_EXECUTE("etl.sync", "etl.sync"),
    ETL_SYNC_RETRY("etl.sync.wait", "etl.sync.wait"),
    /** Post-DAG mart materialization; same routing as {@link #ETL_SYNC_EXECUTE}. */
    ETL_POST_INGEST_MATERIALIZE("etl.sync", "etl.sync"),
    ETL_SYNC_COMPLETED("datapulse.etl.events", ""),
    PRICING_RUN_EXECUTE("pricing.run", "pricing.run"),
    PRICE_ACTION_EXECUTE("price.execution", "price.execution"),
    PRICE_ACTION_RETRY("price.execution.wait", "price.execution.wait"),
    RECONCILIATION_CHECK("price.reconciliation.wait", "price.reconciliation.wait"),
    PROMO_ACTION_EXECUTE("promo.execution", "promo.execution"),
    PROMO_EVALUATION_EXECUTE("promo.evaluation", "promo.evaluation"),
    ETL_PROMO_CAMPAIGN_STALE("datapulse.etl.events", ""),
    REMATERIALIZATION_REQUESTED("etl.sync", "etl.sync"),
    BIDDING_RUN_EXECUTE("bidding.run", "bidding.run"),
    BID_ACTION_EXECUTE("bid.execution", "bid.execution"),
    BID_ACTION_RETRY("bid.execution.wait", "bid.execution.wait");

    private final String exchange;
    private final String routingKey;
}
