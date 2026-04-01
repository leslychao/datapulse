package io.datapulse.execution.domain;

import io.datapulse.execution.config.ExecutionProperties;
import io.datapulse.execution.persistence.PriceActionAttemptEntity;
import io.datapulse.execution.persistence.PriceActionAttemptRepository;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.PriceActionRepository;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private static final String AGGREGATE_TYPE = "price_action";

    private final PriceActionRepository actionRepository;
    private final PriceActionAttemptRepository attemptRepository;
    private final ActionService actionService;
    private final OutboxService outboxService;
    private final ExecutionProperties properties;

    @Transactional
    public void processReconciliationCheck(long actionId, int reconciliationAttempt,
                                           BigDecimal actualPrice, String rawSnapshot) {
        var action = actionRepository.findById(actionId).orElse(null);
        if (action == null || action.getStatus() != ActionStatus.RECONCILIATION_PENDING) {
            log.debug("Reconciliation skipped: actionId={}, currentStatus={}",
                    actionId, action != null ? action.getStatus() : "NOT_FOUND");
            return;
        }

        boolean priceMatch = isPriceMatch(action.getTargetPrice(), actualPrice);

        recordReconciliationEvidence(action, reconciliationAttempt, actualPrice, priceMatch, rawSnapshot);

        if (priceMatch) {
            actionService.casSucceed(actionId, ActionStatus.RECONCILIATION_PENDING,
                    ActionReconciliationSource.AUTO, null);
            log.info("Reconciliation succeeded: actionId={}, targetPrice={}, actualPrice={}",
                    actionId, action.getTargetPrice(), actualPrice);
        } else if (reconciliationAttempt < properties.getReconciliation().getMaxAttempts()) {
            scheduleNextReconciliation(actionId, reconciliationAttempt);
            log.info("Reconciliation mismatch, scheduling retry: actionId={}, attempt={}, target={}, actual={}",
                    actionId, reconciliationAttempt, action.getTargetPrice(), actualPrice);
        } else {
            actionService.casFail(actionId, ActionStatus.RECONCILIATION_PENDING,
                    action.getAttemptCount(), ErrorClassification.PROVIDER_ERROR,
                    "Reconciliation failed: price mismatch after %d checks"
                            .formatted(properties.getReconciliation().getMaxAttempts()));
            log.warn("Reconciliation failed (max attempts): actionId={}, target={}, actual={}",
                    actionId, action.getTargetPrice(), actualPrice);
        }
    }

    @Transactional
    public void processManualReconciliation(long actionId, boolean succeeded,
                                             String manualOverrideReason, Long actorUserId) {
        var action = actionRepository.findById(actionId).orElse(null);
        if (action == null || action.getStatus() != ActionStatus.RECONCILIATION_PENDING) {
            log.warn("Manual reconciliation skipped: actionId={}", actionId);
            return;
        }

        recordManualReconciliationEvidence(action, actorUserId);

        if (succeeded) {
            actionService.casSucceed(actionId, ActionStatus.RECONCILIATION_PENDING,
                    ActionReconciliationSource.MANUAL, manualOverrideReason);
        } else {
            actionService.casFail(actionId, ActionStatus.RECONCILIATION_PENDING,
                    action.getAttemptCount(), ErrorClassification.PROVIDER_ERROR,
                    "Manual reconciliation: marked as failed - " + manualOverrideReason);
        }

        log.info("Manual reconciliation: actionId={}, outcome={}, reason={}",
                actionId, succeeded ? "SUCCEEDED" : "FAILED", manualOverrideReason);
    }

    private boolean isPriceMatch(BigDecimal targetPrice, BigDecimal actualPrice) {
        if (actualPrice == null) {
            return false;
        }
        return targetPrice.compareTo(actualPrice) == 0;
    }

    private void scheduleNextReconciliation(long actionId, int currentAttempt) {
        long delaySeconds = properties.getReconciliation().getInitialDelay().toSeconds();
        int multiplier = properties.getReconciliation().getBackoffMultiplier();
        for (int i = 1; i < currentAttempt; i++) {
            delaySeconds *= multiplier;
        }

        outboxService.createEvent(
                OutboxEventType.RECONCILIATION_CHECK,
                AGGREGATE_TYPE,
                actionId,
                Map.of(
                        "actionId", actionId,
                        "attempt", currentAttempt + 1,
                        "delaySeconds", delaySeconds
                )
        );
    }

    private void recordReconciliationEvidence(PriceActionEntity action,
                                               int reconciliationAttempt,
                                               BigDecimal actualPrice,
                                               boolean priceMatch,
                                               String rawSnapshot) {
        var latestAttempt = attemptRepository
                .findByPriceActionIdAndAttemptNumber(action.getId(), action.getAttemptCount())
                .orElse(null);

        if (latestAttempt != null) {
            latestAttempt.setReconciliationSource(ReconciliationSource.DEFERRED);
            latestAttempt.setReconciliationReadAt(OffsetDateTime.now());
            latestAttempt.setReconciliationSnapshot(rawSnapshot);
            latestAttempt.setActualPrice(actualPrice);
            latestAttempt.setPriceMatch(priceMatch);
            attemptRepository.save(latestAttempt);
        } else {
            log.warn("No attempt found for reconciliation evidence: actionId={}, attemptNumber={}",
                    action.getId(), action.getAttemptCount());
        }
    }

    private void recordManualReconciliationEvidence(PriceActionEntity action, Long actorUserId) {
        var attempt = new PriceActionAttemptEntity();
        attempt.setPriceActionId(action.getId());
        attempt.setAttemptNumber(action.getAttemptCount() + 1);
        attempt.setStartedAt(OffsetDateTime.now());
        attempt.setCompletedAt(OffsetDateTime.now());
        attempt.setReconciliationSource(ReconciliationSource.MANUAL);
        attempt.setReconciliationReadAt(OffsetDateTime.now());
        attempt.setActorUserId(actorUserId);
        attemptRepository.save(attempt);
    }
}
