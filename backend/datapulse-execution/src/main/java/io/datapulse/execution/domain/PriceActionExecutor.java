package io.datapulse.execution.domain;

import io.datapulse.execution.config.ExecutionProperties;
import io.datapulse.execution.domain.gateway.GatewayResult;
import io.datapulse.execution.domain.gateway.PriceActionGateway;
import io.datapulse.execution.persistence.PriceActionAttemptEntity;
import io.datapulse.execution.persistence.PriceActionAttemptRepository;
import io.datapulse.execution.persistence.PriceActionCasRepository;
import io.datapulse.execution.persistence.PriceActionEntity;
import io.datapulse.execution.persistence.PriceActionRepository;
import io.datapulse.platform.observability.MetricsFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates the execution of a price action:
 * 1. CAS claim (SCHEDULED → EXECUTING)
 * 2. Resolve execution context (marketplace, credentials)
 * 3. Create attempt record
 * 4. Dispatch to gateway (LIVE or SIMULATED)
 * 5. Record outcome and transition state
 *
 * Called by RabbitMQ consumer for both PRICE_ACTION_EXECUTE and PRICE_ACTION_RETRY events.
 */
@Slf4j
@Service
public class PriceActionExecutor {

    private final PriceActionRepository actionRepository;
    private final PriceActionAttemptRepository attemptRepository;
    private final PriceActionCasRepository casRepository;
    private final ActionService actionService;
    private final ExecutionCredentialResolver credentialResolver;
    private final ExecutionProperties properties;
    private final MetricsFacade metrics;
    private final Map<ActionExecutionMode, PriceActionGateway> gateways;

    public PriceActionExecutor(PriceActionRepository actionRepository,
                               PriceActionAttemptRepository attemptRepository,
                               PriceActionCasRepository casRepository,
                               ActionService actionService,
                               ExecutionCredentialResolver credentialResolver,
                               ExecutionProperties properties,
                               MetricsFacade metrics,
                               List<PriceActionGateway> gatewayList) {
        this.actionRepository = actionRepository;
        this.attemptRepository = attemptRepository;
        this.casRepository = casRepository;
        this.actionService = actionService;
        this.credentialResolver = credentialResolver;
        this.properties = properties;
        this.metrics = metrics;
        this.gateways = gatewayList.stream()
                .collect(Collectors.toMap(PriceActionGateway::executionMode, Function.identity()));
    }

    public void execute(long actionId) {
        var action = actionRepository.findById(actionId).orElse(null);
        if (action == null) {
            log.warn("Action not found, skipping: actionId={}", actionId);
            return;
        }

        if (!tryClaim(action)) {
            return;
        }

        action = actionRepository.findById(actionId).orElse(null);
        if (action == null) {
            return;
        }

        doExecute(action);
    }

    public void executeRetry(long actionId, int attemptNumber) {
        var action = actionRepository.findById(actionId).orElse(null);
        if (action == null) {
            log.warn("Action not found for retry, skipping: actionId={}", actionId);
            return;
        }

        if (action.getStatus() != ActionStatus.RETRY_SCHEDULED) {
            log.debug("Action not in RETRY_SCHEDULED, skipping: actionId={}, status={}",
                    actionId, action.getStatus());
            return;
        }

        actionService.casExecuteFromRetry(actionId);

        action = actionRepository.findById(actionId).orElse(null);
        if (action == null || action.getStatus() != ActionStatus.EXECUTING) {
            log.debug("CAS retry→executing failed, skipping: actionId={}", actionId);
            return;
        }

        doExecute(action);
    }

    private boolean tryClaim(PriceActionEntity action) {
        if (action.getStatus() == ActionStatus.SCHEDULED) {
            actionService.casClaim(action.getId());
            var refreshed = actionRepository.findById(action.getId()).orElse(null);
            if (refreshed == null || refreshed.getStatus() != ActionStatus.EXECUTING) {
                log.debug("CAS claim skipped: actionId={} (already claimed or superseded)",
                        action.getId());
                return false;
            }
            return true;
        }

        log.debug("Action not in SCHEDULED state, skipping: actionId={}, status={}",
                action.getId(), action.getStatus());
        return false;
    }

    private void doExecute(PriceActionEntity action) {
        OfferExecutionContext context;
        try {
            context = credentialResolver.resolve(action.getMarketplaceOfferId());
        } catch (Exception e) {
            log.error("Failed to resolve execution context: actionId={}, offerId={}, error={}",
                    action.getId(), action.getMarketplaceOfferId(), e.getMessage(), e);
            handleTerminalFailure(action, ErrorClassification.NON_RETRIABLE,
                    "Context resolution failed: " + e.getMessage());
            return;
        }

        int attemptNumber = action.getAttemptCount() + 1;
        var attempt = createAttempt(action.getId(), attemptNumber);

        PriceActionGateway gateway = gateways.get(action.getExecutionMode());
        if (gateway == null) {
            completeAttempt(attempt, AttemptOutcome.NON_RETRIABLE_FAILURE,
                    ErrorClassification.NON_RETRIABLE,
                    "No gateway for execution mode: " + action.getExecutionMode(),
                    null, null);
            handleTerminalFailure(action, ErrorClassification.NON_RETRIABLE,
                    "No gateway for mode: " + action.getExecutionMode());
            return;
        }

        casRepository.casIncrementAttempt(action.getId());

        GatewayResult result = gateway.execute(action, context);

        completeAttempt(attempt, result.outcome(), result.errorClassification(),
                result.errorMessage(),
                result.providerRequestSummary(), result.providerResponseSummary());

        if (result.reconciliationSource() != null) {
            attempt.setReconciliationSource(result.reconciliationSource());
            attempt.setPriceMatch(result.priceMatch());
            attemptRepository.save(attempt);
        }

        handleOutcome(action, result, attemptNumber);
    }

    private void handleOutcome(PriceActionEntity action, GatewayResult result, int attemptNumber) {
        String mode = action.getExecutionMode().name();

        if (result.isSuccess()) {
            if (action.getExecutionMode() == ActionExecutionMode.SIMULATED) {
                actionService.casSucceed(action.getId(), ActionStatus.EXECUTING,
                        ActionReconciliationSource.AUTO, null);
                metrics.incrementCounter("execution.outcome", "result", "succeeded", "mode", mode);
            } else {
                actionService.casReconciliationPending(action.getId());
                metrics.incrementCounter("execution.outcome",
                        "result", "reconciliation_pending", "mode", mode, "reason", "confirmed");
                log.info("Confirmed write, deferred reconciliation: actionId={}, mode={}",
                        action.getId(), mode);
            }
            return;
        }

        if (result.isUncertain()) {
            actionService.casReconciliationPending(action.getId());
            metrics.incrementCounter("execution.outcome",
                    "result", "reconciliation_pending", "mode", mode, "reason", "uncertain");
            return;
        }

        if (result.isRetriable() && attemptNumber < properties.getMaxAttempts()) {
            actionService.casScheduleRetry(action.getId(), attemptNumber);
            metrics.incrementCounter("execution.outcome", "result", "retry_scheduled", "mode", mode);
            return;
        }

        actionService.casFail(action.getId(), ActionStatus.EXECUTING,
                attemptNumber,
                result.errorClassification(),
                result.errorMessage());
        metrics.incrementCounter("execution.outcome", "result", "failed", "mode", mode);
    }

    private void handleTerminalFailure(PriceActionEntity action,
                                        ErrorClassification classification,
                                        String errorMessage) {
        actionService.casFail(action.getId(), ActionStatus.EXECUTING,
                action.getAttemptCount(), classification, errorMessage);
    }

    private PriceActionAttemptEntity createAttempt(long actionId, int attemptNumber) {
        var attempt = new PriceActionAttemptEntity();
        attempt.setPriceActionId(actionId);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setStartedAt(OffsetDateTime.now());
        return attemptRepository.save(attempt);
    }

    private void completeAttempt(PriceActionAttemptEntity attempt,
                                  AttemptOutcome outcome,
                                  ErrorClassification classification,
                                  String errorMessage,
                                  String requestSummary,
                                  String responseSummary) {
        attempt.setCompletedAt(OffsetDateTime.now());
        attempt.setOutcome(outcome);
        attempt.setErrorClassification(classification);
        attempt.setErrorMessage(errorMessage);
        attempt.setProviderRequestSummary(requestSummary);
        attempt.setProviderResponseSummary(responseSummary);
        attemptRepository.save(attempt);
    }
}
