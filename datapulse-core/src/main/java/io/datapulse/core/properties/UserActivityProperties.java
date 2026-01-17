package io.datapulse.core.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.user-activity")
public record UserActivityProperties(
    Duration activityWindow,
    long maxSize,
    Duration cleanupFixedDelay,
    Duration flushFixedDelay,
    int flushBatchSize
) {

}
