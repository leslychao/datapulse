package io.datapulse.etl.config;

import io.datapulse.domain.port.AnalyticsPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config {

  @Bean
  public AnalyticsPort analyticsPort() {
    return () -> { /* no-op */ };
  }
}
