package io.datapulse.core.repository.useractivity;

import java.time.Instant;
import java.util.Map;

public interface UserActivityRepository {

  void updateLastActivityAtIfGreater(Map<Long, Instant> lastSeenByProfileId);

}
