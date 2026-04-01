package io.datapulse.execution.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.ConflictException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.execution.domain.event.ActionCompletedEvent;
import io.datapulse.execution.domain.event.ActionCreatedEvent;
import io.datapulse.execution.domain.event.ActionFailedEvent;
import io.datapulse.execution.persistence.DeferredActionEntity;
import io.datapulse.execution.persistence.DeferredActionRepository;
import io.datapulse.execution.persistence.PriceActionCasRepository;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.PriceActionRepository;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionService {

    private static final String AGGREGATE_TYPE = "price_action";
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration MIN_BACKOFF = Duration.ofSeconds(5);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);
    private static final int BACKOFF_MULTIPLIER = 2;

    private final PriceActionRepository actionRepository;
    private final PriceActionCasRepository casRepository;
    private final DeferredActionRepository deferredActionRepository;
    private final OutboxService outboxService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PriceActionEntity createAction(long workspaceId,
                                          long marketplaceOfferId,
                                          long priceDecisionId,
                                          ActionExecutionMode executionMode,
                                          BigDecimal targetPrice,
                                          BigDecimal currentPrice,
                                          int approvalTimeoutHours,
                                          boolean autoApprove) {
        var existingActive = actionRepository.findActiveByOfferAndMode(marketplaceOfferId, executionMode);

        if (existingActive.isPresent()) {
            var active = existingActive.get();

            if (active.getStatus().isPreExecution()) {
                int rows = casRepository.casSupersede(active.getId(), active.getStatus(), 0);
                if (rows == 0) {
                    log.warn("CAS supersede conflict: actionId={}, status={}", active.getId(), active.getStatus());
                }
            } else {
                deferAction(workspaceId, marketplaceOfferId, priceDecisionId,
                        executionMode, approvalTimeoutHours);
                log.info("Action deferred: offerId={}, activeActionId={}, activeStatus={}",
                        marketplaceOfferId, active.getId(), active.getStatus());
                return null;
            }
        }

        var action = new PriceActionEntity();
        action.setWorkspaceId(workspaceId);
        action.setMarketplaceOfferId(marketplaceOfferId);
        action.setPriceDecisionId(priceDecisionId);
        action.setExecutionMode(executionMode);
        action.setTargetPrice(targetPrice);
        action.setCurrentPriceAtCreation(currentPrice);
        action.setMaxAttempts(DEFAULT_MAX_ATTEMPTS);
        action.setApprovalTimeoutHours(approvalTimeoutHours);

        if (autoApprove) {
            action.setStatus(ActionStatus.APPROVED);
            action.setApprovedAt(OffsetDateTime.now());
        } else {
            action.setStatus(ActionStatus.PENDING_APPROVAL);
        }

        action = actionRepository.save(action);

        if (autoApprove) {
            scheduleExecution(action);
        }

        eventPublisher.publishEvent(new ActionCreatedEvent(
                action.getId(), workspaceId, marketplaceOfferId,
                priceDecisionId, executionMode, targetPrice, currentPrice
        ));

        log.info("Action created: actionId={}, offerId={}, mode={}, status={}, targetPrice={}",
                action.getId(), marketplaceOfferId, executionMode, action.getStatus(), targetPrice);

        return action;
    }

    @Transactional
    public void casApprove(long actionId, Long userId) {
        var action = findActionOrThrow(actionId);
        ensureTransitionAllowed(action, ActionStatus.APPROVED);

        int rows = casRepository.casApprove(actionId, action.getStatus(), userId);
        if (rows == 0) {
            throwCasConflict(actionId, action.getStatus(), ActionStatus.APPROVED);
        }

        action.setStatus(ActionStatus.APPROVED);
        action.setApprovedByUserId(userId);
        action.setApprovedAt(OffsetDateTime.now());

        scheduleExecution(action);

        log.info("Action approved: actionId={}, userId={}", actionId, userId);
    }

    @Transactional
    public void casResume(long actionId, Long userId) {
        var action = findActionOrThrow(actionId);
        ensureTransitionAllowed(action, ActionStatus.APPROVED);

        int rows = casRepository.casApprove(actionId, ActionStatus.ON_HOLD, userId);
        if (rows == 0) {
            throwCasConflict(actionId, action.getStatus(), ActionStatus.APPROVED);
        }

        scheduleExecution(action);

        log.info("Action resumed: actionId={}, userId={}", actionId, userId);
    }

    @Transactional
    public void casHold(long actionId, String holdReason) {
        var action = findActionOrThrow(actionId);
        ensureTransitionAllowed(action, ActionStatus.ON_HOLD);

        int rows = casRepository.casHold(actionId, holdReason);
        if (rows == 0) {
            throwCasConflict(actionId, action.getStatus(), ActionStatus.ON_HOLD);
        }

        log.info("Action put on hold: actionId={}, reason={}", actionId, holdReason);
    }

    @Transactional
    public void casCancel(long actionId, String cancelReason) {
        var action = findActionOrThrow(actionId);

        if (!action.getStatus().isCancellable()) {
            throw ConflictException.of(MessageCodes.EXECUTION_ACTION_NOT_CANCELLABLE,
                    actionId, action.getStatus().name());
        }

        int rows = casRepository.casCancel(actionId, action.getStatus(), cancelReason);
        if (rows == 0) {
            throwCasConflict(actionId, action.getStatus(), ActionStatus.CANCELLED);
        }

        log.info("Action cancelled: actionId={}, reason={}", actionId, cancelReason);
    }

    @Transactional
    public void casClaim(long actionId) {
        int rows = casRepository.casTransition(actionId, ActionStatus.SCHEDULED, ActionStatus.EXECUTING);
        if (rows == 0) {
            log.debug("CAS claim skipped: actionId={} (already claimed or superseded)", actionId);
        }
    }

    @Transactional
    public void casRetryFromExecuting(long actionId) {
        int rows = casRepository.casTransition(actionId, ActionStatus.RETRY_SCHEDULED, ActionStatus.EXECUTING);
        if (rows == 0) {
            log.warn("CAS retry→executing conflict: actionId={}", actionId);
        }
    }

    @Transactional
    public void casSucceed(long actionId, ActionStatus expectedStatus,
                           ActionReconciliationSource reconciliationSource,
                           String manualOverrideReason) {
        var action = findActionOrThrow(actionId);
        ensureTransitionAllowed(action, ActionStatus.SUCCEEDED);

        int rows = casRepository.casSucceed(actionId, expectedStatus,
                reconciliationSource, manualOverrideReason);
        if (rows == 0) {
            throwCasConflict(actionId, expectedStatus, ActionStatus.SUCCEEDED);
        }

        eventPublisher.publishEvent(new ActionCompletedEvent(
                actionId, action.getWorkspaceId(), action.getMarketplaceOfferId(),
                action.getExecutionMode(), action.getTargetPrice(), reconciliationSource
        ));

        log.info("Action succeeded: actionId={}, reconciliationSource={}",
                actionId, reconciliationSource);
    }

    @Transactional
    public void casFail(long actionId, ActionStatus expectedStatus,
                        int attemptCount, ErrorClassification lastErrorClassification,
                        String lastErrorMessage) {
        var action = findActionOrThrow(actionId);

        int rows = casRepository.casFail(actionId, expectedStatus, attemptCount);
        if (rows == 0) {
            throwCasConflict(actionId, expectedStatus, ActionStatus.FAILED);
        }

        eventPublisher.publishEvent(new ActionFailedEvent(
                actionId, action.getWorkspaceId(), action.getMarketplaceOfferId(),
                action.getExecutionMode(), action.getTargetPrice(),
                attemptCount, lastErrorClassification, lastErrorMessage
        ));

        log.info("Action failed: actionId={}, attempts={}, error={}",
                actionId, attemptCount, lastErrorClassification);
    }

    @Transactional
    public void casScheduleRetry(long actionId, int attemptCount) {
        Duration delay = calculateBackoff(attemptCount);
        OffsetDateTime nextAttempt = OffsetDateTime.now().plus(delay);

        int rows = casRepository.casRetryScheduled(actionId, attemptCount, nextAttempt);
        if (rows == 0) {
            log.warn("CAS retry-schedule conflict: actionId={}", actionId);
            return;
        }

        outboxService.createEvent(
                OutboxEventType.PRICE_ACTION_RETRY,
                AGGREGATE_TYPE,
                actionId,
                Map.of("actionId", actionId, "attemptNumber", attemptCount + 1)
        );

        log.info("Retry scheduled: actionId={}, attempt={}, nextAttemptAt={}",
                actionId, attemptCount + 1, nextAttempt);
    }

    @Transactional
    public void casReconciliationPending(long actionId) {
        int rows = casRepository.casTransition(actionId, ActionStatus.EXECUTING,
                ActionStatus.RECONCILIATION_PENDING);
        if (rows == 0) {
            log.warn("CAS reconciliation-pending conflict: actionId={}", actionId);
            return;
        }

        outboxService.createEvent(
                OutboxEventType.RECONCILIATION_CHECK,
                AGGREGATE_TYPE,
                actionId,
                Map.of("actionId", actionId, "attempt", 1)
        );

        log.info("Reconciliation pending: actionId={}", actionId);
    }

    private void scheduleExecution(PriceActionEntity action) {
        int rows = casRepository.casTransition(action.getId(), ActionStatus.APPROVED, ActionStatus.SCHEDULED);
        if (rows == 0) {
            log.warn("CAS schedule conflict: actionId={}", action.getId());
            return;
        }

        outboxService.createEvent(
                OutboxEventType.PRICE_ACTION_EXECUTE,
                AGGREGATE_TYPE,
                action.getId(),
                Map.of("actionId", action.getId())
        );
    }

    private void deferAction(long workspaceId, long marketplaceOfferId,
                             long priceDecisionId, ActionExecutionMode executionMode,
                             int approvalTimeoutHours) {
        var existing = deferredActionRepository
                .findByMarketplaceOfferIdAndExecutionMode(marketplaceOfferId, executionMode);

        if (existing.isPresent()) {
            var deferred = existing.get();
            deferred.setPriceDecisionId(priceDecisionId);
            deferred.setDeferredReason("Superseded by newer decision");
            deferred.setExpiresAt(OffsetDateTime.now().plusHours(approvalTimeoutHours));
            deferredActionRepository.save(deferred);
        } else {
            var deferred = new DeferredActionEntity();
            deferred.setWorkspaceId(workspaceId);
            deferred.setMarketplaceOfferId(marketplaceOfferId);
            deferred.setPriceDecisionId(priceDecisionId);
            deferred.setExecutionMode(executionMode);
            deferred.setDeferredReason("Active in-flight action exists");
            deferred.setExpiresAt(OffsetDateTime.now().plusHours(approvalTimeoutHours));
            deferredActionRepository.save(deferred);
        }
    }

    private Duration calculateBackoff(int attemptCount) {
        long delaySeconds = MIN_BACKOFF.toSeconds();
        for (int i = 1; i < attemptCount; i++) {
            delaySeconds = Math.min(delaySeconds * BACKOFF_MULTIPLIER, MAX_BACKOFF.toSeconds());
        }
        return Duration.ofSeconds(delaySeconds);
    }

    private PriceActionEntity findActionOrThrow(long actionId) {
        return actionRepository.findById(actionId)
                .orElseThrow(() -> NotFoundException.entity("price_action", actionId));
    }

    private void ensureTransitionAllowed(PriceActionEntity action, ActionStatus targetStatus) {
        if (!action.getStatus().canTransitionTo(targetStatus)) {
            throw ConflictException.of(MessageCodes.EXECUTION_ACTION_INVALID_TRANSITION,
                    action.getId(), action.getStatus().name(), targetStatus.name());
        }
    }

    private void throwCasConflict(long actionId, ActionStatus expected, ActionStatus target) {
        log.warn("CAS conflict: actionId={}, expected={}, target={}", actionId, expected, target);
        throw ConflictException.of(MessageCodes.EXECUTION_ACTION_CAS_CONFLICT,
                actionId, expected.name(), target.name());
    }
}
