package io.datapulse.etl.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IngestProperties.class)
public class IngestConfig {

  @Bean
  Clock ingestClock() {
    return Clock.systemDefaultZone();
  }
}
