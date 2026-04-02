package io.datapulse.etl.scheduling;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.integration.domain.SyncStatus;
import io.datapulse.integration.persistence.MarketplaceSyncStateEntity;
import io.datapulse.integration.persistence.MarketplaceSyncStateRepository;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled sync dispatcher. Runs every minute (configurable), scans
 * {@code marketplace_sync_state} for connections due for sync, creates
 * {@code job_execution} + outbox event for each eligible connection.
 *
 * <p>Distributed lock via ShedLock prevents duplicate execution
 * across multiple API instances.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncScheduler {

    private final MarketplaceSyncStateRepository syncStateRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final OutboxService outboxService;

    @Scheduled(fixedDelayString = "${datapulse.etl.sync-poll-interval:PT1M}")
    @SchedulerLock(name = "syncScheduler", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void pollAndDispatch() {
        log.debug("Sync scheduler poll started");

        List<MarketplaceSyncStateEntity> eligible = syncStateRepository.findEligibleForSync(OffsetDateTime.now());

        if (eligible.isEmpty()) {
            log.debug("No connections eligible for sync");
            return;
        }

        log.info("Found {} eligible sync states", eligible.size());

        // Group by connection_id to dispatch one job per connection
        eligible.stream()
                .map(MarketplaceSyncStateEntity::getMarketplaceConnectionId)
                .distinct()
                .forEach(this::dispatchIfNotActive);
    }

    @Transactional
    protected void dispatchIfNotActive(Long connectionId) {
        if (jobExecutionRepository.existsActiveForConnection(connectionId)) {
            log.debug("Active job already exists for connection: connectionId={}", connectionId);
            return;
        }

        long jobId = jobExecutionRepository.insert(connectionId, "INCREMENTAL");

        outboxService.createEvent(
                OutboxEventType.ETL_SYNC_EXECUTE,
                "job_execution",
                jobId,
                Map.of("jobExecutionId", jobId, "connectionId", connectionId));

        updateNextScheduledAt(connectionId);

        log.info("Sync dispatched: connectionId={}, jobExecutionId={}", connectionId, jobId);
    }

    private void updateNextScheduledAt(Long connectionId) {
        List<MarketplaceSyncStateEntity> states =
                syncStateRepository.findAllByMarketplaceConnectionId(connectionId);
        OffsetDateTime now = OffsetDateTime.now();
        for (MarketplaceSyncStateEntity state : states) {
            state.setStatus(SyncStatus.SYNCING.name());
            state.setLastSyncAt(now);
            state.setNextScheduledAt(now.plusHours(6));
            state.setErrorMessage(null);
        }
        syncStateRepository.saveAll(states);
    }
}
