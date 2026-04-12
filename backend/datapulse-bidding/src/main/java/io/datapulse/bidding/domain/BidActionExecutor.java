package io.datapulse.bidding.domain;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.datapulse.bidding.persistence.BidActionAttemptEntity;
import io.datapulse.bidding.persistence.BidActionAttemptRepository;
import io.datapulse.bidding.persistence.BidActionEntity;
import io.datapulse.bidding.persistence.BidActionRepository;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidActionExecutor {

  private final BidActionRepository actionRepository;
  private final BidActionAttemptRepository attemptRepository;
  private final BidActionGatewayRegistry gatewayRegistry;
  private final BiddingCredentialResolver credentialResolver;
  private final OutboxService outboxService;

  @Transactional
  public void execute(long bidActionId) {
    BidActionEntity action = actionRepository.findById(bidActionId).orElse(null);
    if (action == null) {
      log.warn("Bid action not found, skipping: bidActionId={}", bidActionId);
      return;
    }

    if (!isExecutable(action.getStatus())) {
      log.debug("Bid action not in executable state, skipping: "
              + "bidActionId={}, status={}",
          bidActionId, action.getStatus());
      return;
    }

    action.setStatus(BidActionStatus.EXECUTING);
    actionRepository.save(action);

    Map<String, String> credentials;
    try {
      credentials = resolveCredentials(action.getConnectionId());
    } catch (Exception e) {
      log.error("Failed to resolve credentials: bidActionId={}, "
              + "connectionId={}, error={}",
          bidActionId, action.getConnectionId(), e.getMessage(), e);
      failAction(action, "TOKEN_RESOLUTION_FAILED", e.getMessage());
      return;
    }

    BidActionGateway gateway;
    try {
      ExecutionMode mode = ExecutionMode.valueOf(action.getExecutionMode());
      gateway = gatewayRegistry.resolve(action.getMarketplaceType(), mode);
    } catch (Exception e) {
      log.error("No gateway for marketplace: bidActionId={}, type={}",
          bidActionId, action.getMarketplaceType());
      failAction(action, "NO_GATEWAY", e.getMessage());
      return;
    }

    BidActionGatewayResult result = gateway.execute(action, credentials);

    int attemptNumber = action.getRetryCount() + 1;
    recordAttempt(action.getId(), attemptNumber, result);

    if (result.success()) {
      action.setStatus(BidActionStatus.RECONCILIATION_PENDING);
      action.setExecutedAt(OffsetDateTime.now());
      actionRepository.save(action);

      outboxService.createEvent(
          OutboxEventType.BID_ACTION_RECONCILE,
          "bid_action",
          action.getId(),
          Map.of("bidActionId", action.getId(),
              "eventType", "RECONCILE"));

      log.info("Bid action executed, reconciliation pending: "
              + "bidActionId={}, offerId={}, targetBid={}",
          bidActionId, action.getMarketplaceOfferId(),
          action.getTargetBid());
    } else {
      handleFailure(action, result);
    }
  }

  @Transactional
  public void reconcile(long bidActionId) {
    BidActionEntity action = actionRepository.findById(bidActionId).orElse(null);
    if (action == null) {
      log.warn("Bid action not found for reconciliation: bidActionId={}",
          bidActionId);
      return;
    }

    if (action.getStatus() != BidActionStatus.RECONCILIATION_PENDING) {
      log.debug("Bid action not pending reconciliation, skipping: "
              + "bidActionId={}, status={}",
          bidActionId, action.getStatus());
      return;
    }

    Map<String, String> credentials;
    try {
      credentials = resolveCredentials(action.getConnectionId());
    } catch (Exception e) {
      log.error("Reconciliation credential resolve failed: "
              + "bidActionId={}, error={}",
          bidActionId, e.getMessage(), e);
      failAction(action, "RECONCILE_TOKEN_FAILED", e.getMessage());
      return;
    }

    BidActionGateway gateway;
    try {
      ExecutionMode mode = ExecutionMode.valueOf(action.getExecutionMode());
      gateway = gatewayRegistry.resolve(action.getMarketplaceType(), mode);
    } catch (Exception e) {
      log.error("No gateway for reconciliation: bidActionId={}, type={}",
          bidActionId, action.getMarketplaceType());
      failAction(action, "NO_GATEWAY", e.getMessage());
      return;
    }

    BidActionGatewayResult result = gateway.reconcile(action, credentials);

    if (result.success()) {
      action.setStatus(BidActionStatus.SUCCEEDED);
      action.setReconciledAt(OffsetDateTime.now());
      actionRepository.save(action);
      log.info("Bid action reconciled OK: bidActionId={}, offerId={}",
          bidActionId, action.getMarketplaceOfferId());
    } else {
      log.warn("Bid action reconciliation failed: bidActionId={}, "
              + "offerId={}, error={}",
          bidActionId, action.getMarketplaceOfferId(),
          result.errorMessage());
      failAction(action, "RECONCILE_MISMATCH", result.errorMessage());
    }
  }

  private boolean isExecutable(BidActionStatus status) {
    return status == BidActionStatus.APPROVED
        || status == BidActionStatus.SCHEDULED
        || status == BidActionStatus.RETRY_SCHEDULED;
  }

  private Map<String, String> resolveCredentials(long connectionId) {
    return credentialResolver.resolve(connectionId);
  }

  private void handleFailure(BidActionEntity action,
      BidActionGatewayResult result) {
    if (action.getRetryCount() < action.getMaxRetries()) {
      action.setRetryCount(action.getRetryCount() + 1);
      action.setStatus(BidActionStatus.RETRY_SCHEDULED);
      action.setErrorMessage(result.errorMessage());
      actionRepository.save(action);

      outboxService.createEvent(
          OutboxEventType.BID_ACTION_RETRY,
          "bid_action",
          action.getId(),
          Map.of(
              "bidActionId", action.getId(),
              "attemptNumber", action.getRetryCount()));

      log.info("Bid action retry scheduled: bidActionId={}, "
              + "retry={}/{}",
          action.getId(), action.getRetryCount(), action.getMaxRetries());
    } else {
      failAction(action, result.errorCode(), result.errorMessage());
    }
  }

  private void failAction(BidActionEntity action, String errorCode,
      String errorMessage) {
    action.setStatus(BidActionStatus.FAILED);
    action.setErrorMessage(errorCode != null
        ? "%s: %s".formatted(errorCode, errorMessage)
        : errorMessage);
    actionRepository.save(action);

    log.warn("Bid action failed: bidActionId={}, offerId={}, error={}",
        action.getId(), action.getMarketplaceOfferId(),
        action.getErrorMessage());
  }

  private void recordAttempt(long bidActionId, int attemptNumber,
      BidActionGatewayResult result) {
    var attempt = new BidActionAttemptEntity();
    attempt.setBidActionId(bidActionId);
    attempt.setAttemptNumber(attemptNumber);
    attempt.setRequestSummary(null);
    attempt.setResponseSummary(result.rawResponse());

    if (result.success()) {
      attempt.setStatus(AttemptStatus.SUCCESS);
    } else {
      attempt.setStatus(AttemptStatus.FAILURE);
      attempt.setErrorCode(result.errorCode());
    }

    attemptRepository.save(attempt);
  }
}
