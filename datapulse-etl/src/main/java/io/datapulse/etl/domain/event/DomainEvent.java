package io.datapulse.etl.domain.event;

import java.time.Instant;

public interface DomainEvent {

  Instant occurredAt();
}
