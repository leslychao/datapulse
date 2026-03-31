package io.datapulse.etl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "datapulse.etl.retention")
public record RetentionProperties(
        int financeMonths,
        int flowMonths,
        int stateKeepCount
) {}
