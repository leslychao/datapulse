package io.datapulse.api.outbox;

import io.datapulse.platform.outbox.OutboxRuntime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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
