package io.datapulse.etl.integration.storage;

import java.util.UUID;

public interface MaterializationStorage {

  void publish(UUID eventId, String location);
}
