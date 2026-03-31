package io.datapulse.etl.scheduling;

import java.util.Map;

import io.datapulse.etl.persistence.JobExecutionRepository;
import io.datapulse.integration.domain.event.ConnectionCreatedEvent;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listens for {@link ConnectionCreatedEvent} and triggers a FULL_SYNC
 * for the newly created marketplace connection.
 *
 * <p>This ensures that when a seller connects their marketplace account,
 * all data is loaded immediately without waiting for the next scheduled sync.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionActivationListener {

    private final JobExecutionRepository jobExecutionRepository;
    private final OutboxService outboxService;

    @EventListener
    @Transactional
    public void onConnectionCreated(ConnectionCreatedEvent event) {
        Long connectionId = event.connectionId();

        if (jobExecutionRepository.existsActiveForConnection(connectionId)) {
            log.info("Active job already exists for new connection, skipping FULL_SYNC: connectionId={}",
                    connectionId);
            return;
        }

        long jobId = jobExecutionRepository.insert(connectionId, "FULL_SYNC");

        outboxService.createEvent(
                OutboxEventType.ETL_SYNC_EXECUTE,
                "job_execution",
                jobId,
                Map.of("jobExecutionId", jobId, "connectionId", connectionId));

        log.info("FULL_SYNC dispatched for new connection: connectionId={}, jobExecutionId={}",
                connectionId, jobId);
    }
}
