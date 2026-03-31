package io.datapulse.etl.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IngestProperties.class)
public class IngestConfig {
}
