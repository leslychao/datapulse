package io.datapulse.etl.integration.messaging;

import io.datapulse.etl.domain.entity.Execution;

public interface ExecutionMessagePublisher {

  void publish(Execution execution);
}
