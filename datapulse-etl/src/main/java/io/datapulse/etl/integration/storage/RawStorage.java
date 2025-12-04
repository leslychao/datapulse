package io.datapulse.etl.integration.storage;

import java.util.UUID;

public interface RawStorage {

  String load(UUID eventId);
}
