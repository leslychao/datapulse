package io.datapulse.etl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "datapulse.clickhouse.migration")
public record ClickHouseMigrationProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("db/clickhouse") String scriptsLocation
) {}
