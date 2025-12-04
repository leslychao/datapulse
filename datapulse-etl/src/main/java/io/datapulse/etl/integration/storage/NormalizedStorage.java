package io.datapulse.etl.integration.storage;

import java.util.UUID;

public interface NormalizedStorage {

  void save(UUID eventId, String normalizedPayload);
}
