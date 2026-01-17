package io.datapulse.core.useractivity;

import io.datapulse.core.properties.UserActivityProperties;
import io.datapulse.core.repository.useractivity.UserActivityRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserActivityWriteBehindStore {

  private final UserActivityRepository userActivityRepository;
  private final UserActivityProperties props;

  private final BlockingQueue<FlushItem> queue = new LinkedBlockingQueue<>();

  public void enqueue(long profileId, Instant lastSeen) {
    queue.add(new FlushItem(profileId, lastSeen));
  }

  @Scheduled(fixedDelayString = "PT1S")
  public void flushBatch() {
    int batchSize = props.flushBatchSize();

    List<FlushItem> drained = new ArrayList<>(batchSize);
    queue.drainTo(drained, batchSize);

    if (drained.isEmpty()) {
      return;
    }

    Map<Long, Instant> lastSeenByProfileId = new HashMap<>(drained.size());
    for (FlushItem item : drained) {
      lastSeenByProfileId.merge(
          item.profileId(),
          item.lastSeen(),
          (a, b) -> a.isAfter(b) ? a : b
      );
    }

    userActivityRepository.updateLastActivityAtIfGreater(lastSeenByProfileId);
  }

  public record FlushItem(long profileId, Instant lastSeen) {

  }
}
