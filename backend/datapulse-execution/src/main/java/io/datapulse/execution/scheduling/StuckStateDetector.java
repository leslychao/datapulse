package io.datapulse.execution.scheduling;

import io.datapulse.execution.config.ExecutionProperties;
import io.datapulse.execution.domain.ActionService;
import io.datapulse.execution.domain.ActionStatus;
import io.datapulse.execution.domain.ErrorClassification;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.PriceActionRepository;
import io.datapulse.platform.observability.MetricsFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
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
    private final MetricsFacade metrics;

    @Scheduled(fixedDelayString = "${datapulse.execution.stuck-state.interval:PT5M}")
    @SchedulerLock(name = "execution_stuckStateDetector", lockAtMostFor = "PT10M")
    public void detectStuckActions() {
        int total = 0;
        total += escalateStuckExecuting();
        total += escalateStuckRetryScheduled();
        total += escalateStuckReconciliation();
        total += escalateStuckScheduled();

        metrics.incrementCounter("execution.stuck_detector.runs");
        if (total > 0) {
            log.info("Stuck detector completed: escalated={}", total);
        }
    }

    private int escalateStuckExecuting() {
        OffsetDateTime cutoff = OffsetDateTime.now()
            .minus(properties.getStuckState().getExecutingTtl());
        List<PriceActionEntity> stuck = actionRepository
                .findStuckInStatus(ActionStatus.EXECUTING, cutoff);

        int escalated = 0;
        for (var action : stuck) {
            log.warn("Stuck EXECUTING action escalated to RECONCILIATION_PENDING: actionId={}, updatedAt={}",
                    action.getId(), action.getUpdatedAt());
            try {
                actionService.casReconciliationPending(action.getId());
                escalated++;
                metrics.incrementCounter("execution.stuck_detector.escalated",
                        "status", "EXECUTING");
            } catch (Exception e) {
                log.error("Failed to escalate stuck EXECUTING action: actionId={}", action.getId(), e);
            }
        }
        return escalated;
    }

    private int escalateStuckRetryScheduled() {
        OffsetDateTime cutoff = OffsetDateTime.now()
            .minus(properties.getStuckState().getRetryScheduledGrace());
        List<PriceActionEntity> stuck = actionRepository
                .findStuckInStatus(ActionStatus.RETRY_SCHEDULED, cutoff);

        int escalated = 0;
        for (var action : stuck) {
            if (action.getNextAttemptAt() != null) {
                continue;
            }
            log.warn("Stuck RETRY_SCHEDULED action failed: actionId={}", action.getId());
            try {
                actionService.casFail(action.getId(), ActionStatus.RETRY_SCHEDULED,
                        action.getAttemptCount(), ErrorClassification.NON_RETRIABLE,
                        "Stuck in RETRY_SCHEDULED past grace period");
                escalated++;
                metrics.incrementCounter("execution.stuck_detector.escalated",
                        "status", "RETRY_SCHEDULED");
            } catch (Exception e) {
                log.error("Failed to escalate stuck RETRY_SCHEDULED action: actionId={}", action.getId(), e);
            }
        }
        return escalated;
    }

    private int escalateStuckReconciliation() {
        OffsetDateTime cutoff = OffsetDateTime.now()
            .minus(properties.getStuckState().getReconciliationPendingTtl());
        List<PriceActionEntity> stuck = actionRepository
                .findStuckInStatus(ActionStatus.RECONCILIATION_PENDING, cutoff);

        int escalated = 0;
        for (var action : stuck) {
            log.warn("Stuck RECONCILIATION_PENDING action failed: actionId={}", action.getId());
            try {
                actionService.casFail(action.getId(), ActionStatus.RECONCILIATION_PENDING,
                        action.getAttemptCount(), ErrorClassification.PROVIDER_ERROR,
                        "Reconciliation timeout exceeded");
                escalated++;
                metrics.incrementCounter("execution.stuck_detector.escalated",
                        "status", "RECONCILIATION_PENDING");
            } catch (Exception e) {
                log.error("Failed to escalate stuck RECONCILIATION_PENDING action: actionId={}", action.getId(), e);
            }
        }
        return escalated;
    }

    private int escalateStuckScheduled() {
        OffsetDateTime cutoff = OffsetDateTime.now()
            .minus(properties.getStuckState().getScheduledTtl());
        List<PriceActionEntity> stuck = actionRepository
                .findStuckInStatus(ActionStatus.SCHEDULED, cutoff);

        int escalated = 0;
        for (var action : stuck) {
            log.warn("Stuck SCHEDULED action failed (outbox delivery failure): actionId={}", action.getId());
            try {
                actionService.casFail(action.getId(), ActionStatus.SCHEDULED,
                        action.getAttemptCount(), ErrorClassification.NON_RETRIABLE,
                        "Stuck in SCHEDULED — outbox delivery failure");
                escalated++;
                metrics.incrementCounter("execution.stuck_detector.escalated",
                        "status", "SCHEDULED");
            } catch (Exception e) {
                log.error("Failed to escalate stuck SCHEDULED action: actionId={}", action.getId(), e);
            }
        }
        return escalated;
    }

}
