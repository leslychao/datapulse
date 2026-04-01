package io.datapulse.execution.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.ConflictException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.execution.config.ExecutionProperties;
import io.datapulse.execution.domain.event.ActionCompletedEvent;
import io.datapulse.execution.domain.event.ActionCreatedEvent;
import io.datapulse.execution.domain.event.ActionFailedEvent;
import io.datapulse.execution.persistence.DeferredActionEntity;
import io.datapulse.execution.persistence.DeferredActionRepository;
import io.datapulse.execution.persistence.PriceActionAttemptEntity;
import io.datapulse.execution.persistence.PriceActionAttemptRepository;
import io.datapulse.execution.persistence.PriceActionCasRepository;
import io.datapulse.execution.persistence.PriceActionDetailRow;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.PriceActionQueryRepository;
import io.datapulse.execution.persistence.PriceActionRepository;
import io.datapulse.execution.persistence.PriceActionStateTransitionEntity;
import io.datapulse.execution.persistence.PriceActionStateTransitionRepository;
import io.datapulse.execution.persistence.PriceActionSummaryRow;
import io.datapulse.execution.persistence.PriceActionTransitionRow;
import io.datapulse.platform.audit.AuditEvent;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import io.datapulse.platform.security.WorkspaceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionService {

    private static final String AGGREGATE_TYPE = "price_action";
    private static final String ENTITY_TYPE = "price_action";

    private final PriceActionRepository actionRepository;
    private final PriceActionCasRepository casRepository;
    private final PriceActionAttemptRepository attemptRepository;
    private final PriceActionQueryRepository queryRepository;
    private final PriceActionStateTransitionRepository stateTransitionRepository;
    private final DeferredActionRepository deferredActionRepository;
    private final OutboxService outboxService;
    private final ExecutionProperties properties;
    private final WorkspaceContext workspaceContext;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<PriceActionSummaryRow> listActions(long workspaceId,
                                                    PriceActionFilter filter,
                                                    Pageable pageable) {
        return queryRepository.findAll(workspaceId, filter, pageable);
    }

    @Transactional(readOnly = true)
    public PriceActionEntity getAction(long actionId) {
        return findActionOrThrow(actionId);
    }

    @Transactional(readOnly = true)
    public PriceActionDetailRow getActionDetailRow(long actionId) {
        return queryRepository.findDetailById(actionId)
            .orElseThrow(() -> NotFoundException.entity("price_action", actionId));
    }

    @Transactional(readOnly = true)
    public List<PriceActionAttemptEntity> getAttempts(long actionId) {
        return attemptRepository.findByPriceActionIdOrderByAttemptNumber(actionId);
    }

    @Transactional(readOnly = true)
    public List<PriceActionTransitionRow> getTransitions(long actionId) {
        return queryRepository.findTransitions(actionId);
    }

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
                ActionStatus prevStatus = active.getStatus();
                int rows = casRepository.casSupersede(active.getId(), prevStatus, 0);
                if (rows == 0) {
                    log.warn("CAS supersede conflict: actionId={}, status={}", active.getId(), prevStatus);
                } else {
                    recordTransition(active.getId(), prevStatus,
                        ActionStatus.SUPERSEDED, null, null);
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
        action.setMaxAttempts(properties.getMaxAttempts());
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
        ActionStatus prevStatus = action.getStatus();
        ensureTransitionAllowed(action, ActionStatus.APPROVED);

        int rows = casRepository.casApprove(actionId, prevStatus, userId);
        if (rows == 0) {
            throwCasConflict(actionId, prevStatus, ActionStatus.APPROVED);
        }

        recordTransition(actionId, prevStatus, ActionStatus.APPROVED, userId, null);

        action.setStatus(ActionStatus.APPROVED);
        action.setApprovedByUserId(userId);
        action.setApprovedAt(OffsetDateTime.now());

        scheduleExecution(action);

        publishAudit("action.approve", actionId, "SUCCESS", null);
        log.info("Action approved: actionId={}, userId={}", actionId, userId);
    }

    @Transactional
    public void casReject(long actionId, String cancelReason) {
        var action = findActionOrThrow(actionId);

        if (action.getStatus() != ActionStatus.PENDING_APPROVAL) {
            throw ConflictException.of(MessageCodes.EXECUTION_ACTION_INVALID_TRANSITION,
                    actionId, action.getStatus().name(), ActionStatus.CANCELLED.name());
        }

        int rows = casRepository.casCancel(actionId, ActionStatus.PENDING_APPROVAL, cancelReason);
        if (rows == 0) {
            throwCasConflict(actionId, action.getStatus(), ActionStatus.CANCELLED);
        }

        recordTransition(actionId, ActionStatus.PENDING_APPROVAL,
            ActionStatus.CANCELLED, resolveCurrentUserId(), cancelReason);

        publishAudit("action.reject", actionId, "SUCCESS", cancelReason);
        log.info("Action rejected: actionId={}, reason={}", actionId, cancelReason);
    }

    @Transactional
    public void casResume(long actionId, Long userId) {
        var action = findActionOrThrow(actionId);
        ensureTransitionAllowed(action, ActionStatus.APPROVED);

        int rows = casRepository.casApprove(actionId, ActionStatus.ON_HOLD, userId);
        if (rows == 0) {
            throwCasConflict(actionId, action.getStatus(), ActionStatus.APPROVED);
        }

        recordTransition(actionId, ActionStatus.ON_HOLD,
            ActionStatus.APPROVED, userId, null);

        scheduleExecution(action);

        publishAudit("action.resume", actionId, "SUCCESS", null);
        log.info("Action resumed: actionId={}, userId={}", actionId, userId);
    }

    @Transactional
    public void casHold(long actionId, String holdReason) {
        var action = findActionOrThrow(actionId);
        ActionStatus prevStatus = action.getStatus();
        ensureTransitionAllowed(action, ActionStatus.ON_HOLD);

        int rows = casRepository.casHold(actionId, holdReason);
        if (rows == 0) {
            throwCasConflict(actionId, prevStatus, ActionStatus.ON_HOLD);
        }

        recordTransition(actionId, prevStatus, ActionStatus.ON_HOLD,
            resolveCurrentUserId(), holdReason);

        publishAudit("action.hold", actionId, "SUCCESS", holdReason);
        log.info("Action put on hold: actionId={}, reason={}", actionId, holdReason);
    }

    @Transactional
    public void casCancel(long actionId, String cancelReason) {
        var action = findActionOrThrow(actionId);
        ActionStatus prevStatus = action.getStatus();

        if (!prevStatus.isCancellable()) {
            throw ConflictException.of(MessageCodes.EXECUTION_ACTION_NOT_CANCELLABLE,
                    actionId, prevStatus.name());
        }

        int rows = casRepository.casCancel(actionId, prevStatus, cancelReason);
        if (rows == 0) {
            throwCasConflict(actionId, prevStatus, ActionStatus.CANCELLED);
        }

        recordTransition(actionId, prevStatus, ActionStatus.CANCELLED,
            resolveCurrentUserId(), cancelReason);

        publishAudit("action.cancel", actionId, "SUCCESS", cancelReason);
        log.info("Action cancelled: actionId={}, reason={}", actionId, cancelReason);
    }

    @Transactional
    public void retryFailed(long actionId) {
        var action = findActionOrThrow(actionId);

        if (action.getStatus() != ActionStatus.FAILED) {
            throw ConflictException.of(MessageCodes.EXECUTION_ACTION_INVALID_TRANSITION,
                    actionId, action.getStatus().name(), "RETRY");
        }

        createAction(
                action.getWorkspaceId(),
                action.getMarketplaceOfferId(),
                action.getPriceDecisionId(),
                action.getExecutionMode(),
                action.getTargetPrice(),
                action.getCurrentPriceAtCreation(),
                action.getApprovalTimeoutHours(),
                true
        );

        publishAudit("action.retry", actionId, "SUCCESS", null);
        log.info("Retry created for failed action: originalActionId={}", actionId);
    }

    @Transactional
    public void casClaim(long actionId) {
        int rows = casRepository.casTransition(actionId, ActionStatus.SCHEDULED, ActionStatus.EXECUTING);
        if (rows == 0) {
            log.debug("CAS claim skipped: actionId={} (already claimed or superseded)", actionId);
        } else {
            recordTransition(actionId, ActionStatus.SCHEDULED,
                ActionStatus.EXECUTING, null, null);
        }
    }

    @Transactional
    public void casExecuteFromRetry(long actionId) {
        int rows = casRepository.casTransition(actionId,
                ActionStatus.RETRY_SCHEDULED, ActionStatus.EXECUTING);
        if (rows == 0) {
            log.warn("CAS retry→executing conflict: actionId={}", actionId);
        } else {
            recordTransition(actionId, ActionStatus.RETRY_SCHEDULED,
                ActionStatus.EXECUTING, null, null);
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

        recordTransition(actionId, expectedStatus, ActionStatus.SUCCEEDED, null, null);

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

        recordTransition(actionId, expectedStatus, ActionStatus.FAILED,
            null, lastErrorMessage);

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

        recordTransition(actionId, ActionStatus.EXECUTING,
            ActionStatus.RETRY_SCHEDULED, null, null);

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

        recordTransition(actionId, ActionStatus.EXECUTING,
            ActionStatus.RECONCILIATION_PENDING, null, null);

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

        recordTransition(action.getId(), ActionStatus.APPROVED,
            ActionStatus.SCHEDULED, null, null);

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
        long delaySeconds = properties.getMinBackoff().toSeconds();
        long maxSeconds = properties.getMaxBackoff().toSeconds();
        int multiplier = properties.getBackoffMultiplier();
        for (int i = 1; i < attemptCount; i++) {
            delaySeconds = Math.min(delaySeconds * multiplier, maxSeconds);
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
        publishAudit("action.cas_conflict", actionId, "CAS_CONFLICT",
                "expected=%s, target=%s".formatted(expected, target));
        throw ConflictException.of(MessageCodes.EXECUTION_ACTION_CAS_CONFLICT,
                actionId, expected.name(), target.name());
    }

    private void recordTransition(long actionId, ActionStatus fromStatus,
                                   ActionStatus toStatus, Long actorUserId,
                                   String reason) {
        var transition = new PriceActionStateTransitionEntity();
        transition.setPriceActionId(actionId);
        transition.setFromStatus(fromStatus);
        transition.setToStatus(toStatus);
        transition.setActorUserId(actorUserId);
        transition.setReason(reason);
        stateTransitionRepository.save(transition);
    }

    private Long resolveCurrentUserId() {
        try {
            return workspaceContext.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private void publishAudit(String actionType, long actionId,
                              String outcome, String details) {
        long wsId = 0L;
        Long userId = null;
        try {
            wsId = workspaceContext.getWorkspaceId();
            userId = workspaceContext.getUserId();
        } catch (Exception ignored) {
        }

        eventPublisher.publishEvent(new AuditEvent(
                wsId, "USER", userId, actionType,
                ENTITY_TYPE, String.valueOf(actionId),
                outcome, details, null, null
        ));
    }

}
