package io.datapulse.etl.integration.external;

import java.util.UUID;

public interface EventSourceInvoker {

  String invoke(UUID eventId);
}
