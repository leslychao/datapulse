package io.datapulse.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "datapulse.materialization")
public record AnalyticsProperties(
    String dailyRematerializationCron,
    boolean incrementalEnabled,
    String fullRematerializationTimeout,
    boolean optimizeFinalAfterFull,
    int batchSize
) {

  public AnalyticsProperties {
    if (dailyRematerializationCron == null) {
      dailyRematerializationCron = "0 0 2 * * *";
    }
    if (fullRematerializationTimeout == null) {
      fullRematerializationTimeout = "2h";
    }
    if (batchSize <= 0) {
      batchSize = 5000;
    }
  }
}
