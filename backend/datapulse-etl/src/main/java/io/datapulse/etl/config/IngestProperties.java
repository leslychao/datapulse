package io.datapulse.etl.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "datapulse.etl.ingest")
public record IngestProperties(
        @DefaultValue("500") int canonicalBatchSize,
        @DefaultValue("5000") int clickhouseBatchSize,
        @DefaultValue("PT2H") Duration jobTimeout,
        @DefaultValue("3") int maxJobRetries,
        @DefaultValue("PT5M") Duration minRetryBackoff,
        @DefaultValue("PT20M") Duration maxRetryBackoff,
        @DefaultValue("2") int retryBackoffMultiplier,
        @DefaultValue("PT1H") Duration staleRetryThreshold,
        @DefaultValue("PT48H") Duration staleCampaignThreshold,
        /**
         * Safety cap for INCREMENTAL fact capture: never load older than {@code now} minus this many
         * days (even if {@code last_success_at} is missing or very old).
         */
        @DefaultValue("30") int incrementalFactLookbackDays,
        /**
         * Fact date-range start for FULL_SYNC and MANUAL_SYNC (orders/sales/returns/finance
         * date filters). Wider than incremental cap.
         */
        @DefaultValue("365") int fullFactLookbackDays,
        /**
         * Subtract from {@code marketplace_sync_state.last_success_at} when computing incremental
         * fact window start (late-arriving provider data).
         */
        @DefaultValue("PT1H") Duration factSyncOverlap,
        /** Delay before the next scheduled sync attempt after terminal failure (sync state ERROR). */
        @DefaultValue("PT15M") Duration syncNextAttemptAfterError,
        /**
         * Max wall time allowed in {@code MATERIALIZING} before {@link io.datapulse.etl.domain.JobExecutionStatus#STALE}
         * (async post-ingest / ClickHouse phase). Uses {@code job_execution.materializing_at} when set; otherwise
         * falls back to {@code started_at} for legacy rows.
         */
        @DefaultValue("PT1H") Duration materializingStaleThreshold,
        @DefaultValue("SYNC") PostIngestMaterializationMode postIngestMaterializationMode,
        /**
         * When job is already {@code IN_PROGRESS} and Rabbit reports {@code redelivered=false}, still
         * allow the same reclaim {@code UPDATE} as for redelivery if {@code started_at} is older than
         * this threshold. Mitigates silent ack + stuck job when the broker does not set redelivered.
         * Set to {@code PT0S} to disable (only {@code redelivered=true} reclaims).
         */
        @DefaultValue("PT15M") Duration inProgressOrphanReclaimThreshold
) {}
