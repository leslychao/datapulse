package io.datapulse.bidding.scheduling;

import io.datapulse.bidding.persistence.ManualBidLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidLockExpirationScheduler {

  private final ManualBidLockRepository lockRepository;

  @Scheduled(cron = "${datapulse.bidding.lock-expiration-cron:0 */15 * * * *}")
  @SchedulerLock(name = "bidLockExpiration", lockAtMostFor = "PT5M")
  @Transactional
  public void removeExpiredLocks() {
    try {
      int deleted = lockRepository.deleteExpiredLocks();
      if (deleted > 0) {
        log.info("Removed {} expired manual bid locks", deleted);
      }
    } catch (Exception e) {
      log.error("Failed to remove expired bid locks", e);
    }
  }
}
