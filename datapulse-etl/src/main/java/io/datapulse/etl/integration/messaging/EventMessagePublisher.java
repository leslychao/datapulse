package io.datapulse.etl.integration.messaging;

import io.datapulse.etl.domain.entity.Event;

public interface EventMessagePublisher {

  void publish(Event event);
}
