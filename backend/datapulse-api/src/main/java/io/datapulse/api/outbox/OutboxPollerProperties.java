package io.datapulse.api.outbox;

import io.datapulse.platform.outbox.OutboxRuntime;
import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "datapulse.outbox")
public class OutboxPollerProperties {

  private final boolean enabled;
  private final OutboxRuntime runtime;
  private final Duration pollInterval;
  private final int batchSize;
  private final int maxRetryCount;
  private final Duration retryBackoff;
}
