package io.datapulse.core.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "datapulse.user-activity")
public record UserActivityProperties(
    Duration expireAfterWrite,
    long maxSize,
    int flushBatchSize
) {

  public UserActivityProperties {
    expireAfterWrite = expireAfterWrite == null ? Duration.ofMinutes(5) : expireAfterWrite;
    maxSize = maxSize <= 0 ? 200_000L : maxSize;
    flushBatchSize = flushBatchSize <= 0 ? 1000 : flushBatchSize;
  }
}
