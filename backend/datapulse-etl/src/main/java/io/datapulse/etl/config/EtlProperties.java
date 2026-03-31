package io.datapulse.etl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "datapulse.etl")
public record EtlProperties(
        String tempDir
) {}
