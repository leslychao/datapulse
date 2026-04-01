package io.datapulse.execution.scheduling;

import io.datapulse.execution.config.ExecutionProperties;
import io.datapulse.execution.domain.ActionService;
import io.datapulse.execution.domain.ActionStatus;
import io.datapulse.execution.domain.ErrorClassification;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.PriceActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Detects actions stuck in non-terminal states beyond their expected TTL.
 * Escalates per execution.md §Stuck-state detector:
 * - EXECUTING → RECONCILIATION_PENDING (write may have been applied)
 * - RETRY_SCHEDULED (past next_attempt_at + grace) → FAILED
 * - RECONCILIATION_PENDING (past timeout) → FAILED
 * - SCHEDULED (past TTL) → FAILED (outbox delivery failure)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StuckStateDetector {

    private final PriceActionRepository actionRepository;
    private final ActionService actionService;
    private final ExecutionProperties properties;

    @Scheduled(fixedDelayString = "${datapulse.execution.stuck-state.interval:PT5M}")
    public void detectStuckActions() {
        escalateStuckExecuting();
        escalateStuckRetryScheduled();
        escalateStuckReconciliation();
        escalateStuckScheduled();
    }

    private void escalateStuckExecuting() {
        int ttlMinutes = toMinutes(properties.getStuckState().getExecutingTtl());
        List<PriceActionEntity> stuck = actionRepository
                .findStuckInStatus(ActionStatus.EXECUTING.name(), ttlMinutes);

        for (var action : stuck) {
            log.warn("Stuck EXECUTING action escalated to RECONCILIATION_PENDING: actionId={}, updatedAt={}",
                    action.getId(), action.getUpdatedAt());
            try {
                actionService.casReconciliationPending(action.getId());
            } catch (Exception e) {
                log.error("Failed to escalate stuck EXECUTING action: actionId={}", action.getId(), e);
            }
        }
    }

    private void escalateStuckRetryScheduled() {
        int ttlMinutes = toMinutes(properties.getStuckState().getRetryScheduledGrace());
        List<PriceActionEntity> stuck = actionRepository
                .findStuckInStatus(ActionStatus.RETRY_SCHEDULED.name(), ttlMinutes);

        for (var action : stuck) {
            if (action.getNextAttemptAt() != null) {
                continue;
            }
            log.warn("Stuck RETRY_SCHEDULED action failed: actionId={}", action.getId());
            try {
                actionService.casFail(action.getId(), ActionStatus.RETRY_SCHEDULED,
                        action.getAttemptCount(), ErrorClassification.NON_RETRIABLE,
                        "Stuck in RETRY_SCHEDULED past grace period");
            } catch (Exception e) {
                log.error("Failed to escalate stuck RETRY_SCHEDULED action: actionId={}", action.getId(), e);
            }
        }
    }

    private void escalateStuckReconciliation() {
        int ttlMinutes = toMinutes(properties.getStuckState().getReconciliationPendingTtl());
        List<PriceActionEntity> stuck = actionRepository
                .findStuckInStatus(ActionStatus.RECONCILIATION_PENDING.name(), ttlMinutes);

        for (var action : stuck) {
            log.warn("Stuck RECONCILIATION_PENDING action failed: actionId={}", action.getId());
            try {
                actionService.casFail(action.getId(), ActionStatus.RECONCILIATION_PENDING,
                        action.getAttemptCount(), ErrorClassification.PROVIDER_ERROR,
                        "Reconciliation timeout exceeded");
            } catch (Exception e) {
                log.error("Failed to escalate stuck RECONCILIATION_PENDING action: actionId={}", action.getId(), e);
            }
        }
    }

    private void escalateStuckScheduled() {
        int ttlMinutes = toMinutes(properties.getStuckState().getScheduledTtl());
        List<PriceActionEntity> stuck = actionRepository
                .findStuckInStatus(ActionStatus.SCHEDULED.name(), ttlMinutes);

        for (var action : stuck) {
            log.warn("Stuck SCHEDULED action failed (outbox delivery failure): actionId={}", action.getId());
            try {
                actionService.casFail(action.getId(), ActionStatus.SCHEDULED,
                        action.getAttemptCount(), ErrorClassification.NON_RETRIABLE,
                        "Stuck in SCHEDULED — outbox delivery failure");
            } catch (Exception e) {
                log.error("Failed to escalate stuck SCHEDULED action: actionId={}", action.getId(), e);
            }
        }
    }

    private int toMinutes(Duration duration) {
        return (int) duration.toMinutes();
    }
}
