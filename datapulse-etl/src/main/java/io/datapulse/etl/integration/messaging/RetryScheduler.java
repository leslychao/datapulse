package io.datapulse.etl.integration.messaging;

import io.datapulse.etl.domain.entity.Execution;
import java.time.Duration;

public interface RetryScheduler {

  void schedule(Execution execution, Duration delay);
}
