package io.datapulse.core.useractivity;

import io.datapulse.core.properties.UserActivityProperties;
import io.datapulse.core.repository.useractivity.UserActivityRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserActivityWriteBehindStore {

  private final UserActivityRepository userActivityRepository;
  private final UserActivityProperties props;

  private final ConcurrentHashMap<Long, Instant> pendingLastSeen = new ConcurrentHashMap<>();

  public void enqueue(long profileId, Instant lastSeenAt) {
    pendingLastSeen.merge(
        profileId,
        lastSeenAt,
        (a, b) -> a.isAfter(b) ? a : b
    );
  }

  @Scheduled(fixedDelayString = "${app.user-activity.flush-fixed-delay}")
  public void flushBatch() {
    if (pendingLastSeen.isEmpty()) {
      return;
    }

    int batchSize = props.flushBatchSize();
    List<Map.Entry<Long, Instant>> batch = new ArrayList<>(batchSize);

    for (Map.Entry<Long, Instant> entry : pendingLastSeen.entrySet()) {
      batch.add(entry);
      if (batch.size() >= batchSize) {
        break;
      }
    }

    Map<Long, Instant> toFlush = new ConcurrentHashMap<>(batch.size());
    for (Map.Entry<Long, Instant> entry : batch) {
      Long profileId = entry.getKey();
      Instant lastSeenAt = entry.getValue();
      boolean removed = pendingLastSeen.remove(profileId, lastSeenAt);
      if (removed) {
        toFlush.put(profileId, lastSeenAt);
      }
    }

    if (!toFlush.isEmpty()) {
      userActivityRepository.updateLastActivityAtIfGreater(toFlush);
    }
  }
}
