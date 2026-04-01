package io.datapulse.execution.scheduling;

import io.datapulse.execution.domain.ActionExecutionMode;
import io.datapulse.execution.domain.ActionService;
import io.datapulse.execution.persistence.DeferredActionEntity;
import io.datapulse.execution.persistence.DeferredActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Processes deferred actions: when an in-flight action completes,
 * this job picks up deferred actions that are now eligible for creation.
 *
 * Also cleans up expired deferred actions.
 * Runs every 30 seconds per execution.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeferredActionProcessor {

    private final DeferredActionRepository deferredActionRepository;
    private final ActionService actionService;

    @Scheduled(fixedDelayString = "PT30S")
    public void processDeferred() {
        cleanupExpired();
        processPendingDeferred();
    }

    private void processPendingDeferred() {
        List<DeferredActionEntity> ready = deferredActionRepository
                .findReadyToExecute(OffsetDateTime.now());

        for (var deferred : ready) {
            try {
                createActionFromDeferred(deferred);
                deferredActionRepository.delete(deferred);
                log.info("Deferred action processed: offerId={}, decisionId={}",
                        deferred.getMarketplaceOfferId(), deferred.getPriceDecisionId());
            } catch (Exception e) {
                log.error("Failed to process deferred action: id={}, offerId={}",
                        deferred.getId(), deferred.getMarketplaceOfferId(), e);
            }
        }
    }

    @Transactional
    void createActionFromDeferred(DeferredActionEntity deferred) {
        boolean autoApprove = deferred.getExecutionMode() == ActionExecutionMode.SIMULATED;

        actionService.createAction(
                deferred.getWorkspaceId(),
                deferred.getMarketplaceOfferId(),
                deferred.getPriceDecisionId(),
                deferred.getExecutionMode(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                24,
                autoApprove
        );
    }

    private void cleanupExpired() {
        int deleted = deferredActionRepository.deleteExpired(OffsetDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired deferred actions", deleted);
        }
    }
}
