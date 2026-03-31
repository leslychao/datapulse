package io.datapulse.etl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "datapulse.s3")
public record S3Properties(
        String endpoint,
        String accessKey,
        String secretKey,
        String rawBucket
) {}
