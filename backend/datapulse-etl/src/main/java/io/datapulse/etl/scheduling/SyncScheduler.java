package io.datapulse.etl.scheduling;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import io.datapulse.integration.persistence.MarketplaceSyncStateEntity;
import io.datapulse.integration.persistence.MarketplaceSyncStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled sync dispatcher. Runs every minute (configurable), scans
 * {@code marketplace_sync_state} for connections due for sync, delegates
 * dispatch to {@link SyncDispatcher} (separate bean for correct {@code @Transactional} proxy).
 *
 * <p>Distributed lock via ShedLock prevents duplicate execution
 * across multiple API instances.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncScheduler {

  private final MarketplaceSyncStateRepository syncStateRepository;
  private final SyncDispatcher syncDispatcher;
  private final Clock clock;

  @Scheduled(fixedDelayString = "${datapulse.etl.sync-poll-interval:PT1M}")
  @SchedulerLock(name = "syncScheduler", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
  public void pollAndDispatch() {
    log.debug("Sync scheduler poll started");

    OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    List<MarketplaceSyncStateEntity> eligible = syncStateRepository.findEligibleForSync(now);

    if (eligible.isEmpty()) {
      log.debug("No connections eligible for sync");
      return;
    }

    log.info("Found {} eligible sync states", eligible.size());

    eligible.stream()
        .map(MarketplaceSyncStateEntity::getMarketplaceConnectionId)
        .distinct()
        .forEach(syncDispatcher::dispatchIfNotActive);
  }
}
