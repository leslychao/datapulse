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
        @DefaultValue("30") int incrementalFactLookbackDays
) {}
