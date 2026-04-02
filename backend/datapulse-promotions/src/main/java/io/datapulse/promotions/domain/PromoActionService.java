package io.datapulse.promotions.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.ConflictException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.platform.audit.AuditEvent;
import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.promotions.adapter.ozon.OzonPromoWriteAdapter;
import io.datapulse.promotions.adapter.simulated.SimulatedPromoWriteAdapter;
import io.datapulse.promotions.api.BulkPromoActionRequest;
import io.datapulse.promotions.api.BulkPromoActionResponse;
import io.datapulse.promotions.api.PromoActionMapper;
import io.datapulse.promotions.api.PromoActionResponse;
import io.datapulse.promotions.persistence.PromoActionAttemptEntity;
import io.datapulse.promotions.persistence.PromoActionAttemptRepository;
import io.datapulse.promotions.persistence.PromoActionEntity;
import io.datapulse.promotions.persistence.PromoActionQueryRepository;
import io.datapulse.promotions.persistence.PromoActionRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoActionService {

  private static final String ENTITY_TYPE = "promo_action";

  private final PromoActionRepository actionRepository;
  private final PromoActionQueryRepository actionQueryRepository;
  private final PromoActionMapper actionMapper;
  private final ApplicationEventPublisher eventPublisher;
  private final WorkspaceContext workspaceContext;
  private final PromoActionAttemptRepository attemptRepository;
  private final OzonPromoWriteAdapter ozonAdapter;
  private final SimulatedPromoWriteAdapter simulatedAdapter;
  private final PromoCredentialResolver credentialResolver;
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  private static final int MAX_ATTEMPTS = 2;

  @Transactional
  public void executeAction(long actionId) {
    PromoActionEntity action = actionRepository.findById(actionId)
        .orElseThrow(() -> new IllegalStateException("PromoAction not found: " + actionId));

    if (action.getExecutionMode() == PromoExecutionMode.SIMULATED) {
      executeSimulated(action);
      return;
    }

    int updated = actionRepository.casUpdateStatus(
        actionId, PromoActionStatus.APPROVED, PromoActionStatus.EXECUTING);
    if (updated == 0) {
      log.warn("PromoAction {} is not APPROVED (CAS failed), skipping execution", actionId);
      return;
    }
    action.setStatus(PromoActionStatus.EXECUTING);

    ActionContext ctx = loadActionContext(action);
    if (ctx == null) {
      failAction(action, "Failed to load action context (campaign/offer not found)");
      return;
    }

    executeWithRetry(action, ctx);
  }

  private void executeSimulated(PromoActionEntity action) {
    int updated = actionRepository.casUpdateStatus(
        action.getId(), PromoActionStatus.APPROVED, PromoActionStatus.EXECUTING);
    if (updated == 0) {
      log.warn("Simulated PromoAction {} is not APPROVED, skipping", action.getId());
      return;
    }

    OzonPromoWriteAdapter.PromoWriteResult result;
    if (action.getActionType() == PromoActionType.ACTIVATE) {
      result = simulatedAdapter.simulateActivate(0L,
          List.of(new OzonPromoWriteAdapter.ActivateProductRequest(
              0L, action.getTargetPromoPrice(), null)));
    } else {
      result = simulatedAdapter.simulateDeactivate(0L, List.of(0L));
    }

    recordAttempt(action, 1, PromoAttemptOutcome.SUCCESS, null,
        "{\"simulated\":true}", result.rawResponse());
    succeedAction(action);
    log.info("Simulated promo action completed: actionId={}, type={}",
        action.getId(), action.getActionType());
  }

  private void executeWithRetry(PromoActionEntity action, ActionContext ctx) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      action.setAttemptCount(attempt);
      actionRepository.save(action);

      try {
        OzonPromoWriteAdapter.PromoWriteResult result = callProvider(action, ctx);
        boolean accepted = result.isAccepted(ctx.externalProductId());

        if (accepted) {
          recordAttempt(action, attempt, PromoAttemptOutcome.SUCCESS, null,
              buildRequestSummary(action, ctx), result.rawResponse());
          succeedAction(action);
          updateCanonicalStatus(action);
          return;
        }

        String rejectReason = result.rejected().stream()
            .filter(r -> r.productId() == ctx.externalProductId())
            .map(OzonPromoWriteAdapter.PromoWriteResult.RejectedProduct::reason)
            .findFirst()
            .orElse("Unknown rejection reason");

        recordAttempt(action, attempt, PromoAttemptOutcome.NON_RETRIABLE_FAILURE,
            rejectReason, buildRequestSummary(action, ctx), result.rawResponse());
        failAction(action, rejectReason);
        return;

      } catch (Exception e) {
        boolean retryable = isRetryable(e);
        recordAttempt(action,
            attempt,
            retryable ? PromoAttemptOutcome.RETRIABLE_FAILURE : PromoAttemptOutcome.NON_RETRIABLE_FAILURE,
            e.getMessage(), buildRequestSummary(action, ctx),
            null);

        if (!retryable || attempt >= MAX_ATTEMPTS) {
          failAction(action, e.getMessage());
          return;
        }

        sleep(2000L * attempt);
      }
    }
  }

  private OzonPromoWriteAdapter.PromoWriteResult callProvider(PromoActionEntity action,
      ActionContext ctx) {
    if (action.getActionType() == PromoActionType.ACTIVATE) {
      return ozonAdapter.activateProducts(ctx.clientId(), ctx.apiKey(),
          ctx.externalActionId(),
          List.of(new OzonPromoWriteAdapter.ActivateProductRequest(
              ctx.externalProductId(), action.getTargetPromoPrice(), null)));
    } else {
      return ozonAdapter.deactivateProducts(ctx.clientId(), ctx.apiKey(),
          ctx.externalActionId(),
          List.of(ctx.externalProductId()));
    }
  }

  private void succeedAction(PromoActionEntity action) {
    actionRepository.casUpdateStatus(
        action.getId(), PromoActionStatus.EXECUTING, PromoActionStatus.SUCCEEDED);
    log.info("Promo action succeeded: actionId={}, type={}", action.getId(),
        action.getActionType());
  }

  private void failAction(PromoActionEntity action, String error) {
    action.setLastError(error);
    action.setStatus(PromoActionStatus.FAILED);
    actionRepository.save(action);
    log.warn("Promo action failed: actionId={}, error={}", action.getId(), error);
  }

  private void updateCanonicalStatus(PromoActionEntity action) {
    String newStatus = action.getActionType() == PromoActionType.ACTIVATE
        ? "PARTICIPATING" : "REMOVED";
    String expectedStatus = action.getActionType() == PromoActionType.ACTIVATE
        ? "ELIGIBLE" : "PARTICIPATING";
    String source = "AUTO";

    var params = new MapSqlParameterSource()
        .addValue("offerId", action.getMarketplaceOfferId())
        .addValue("campaignId", action.getCanonicalPromoCampaignId())
        .addValue("newStatus", newStatus)
        .addValue("expectedStatus", expectedStatus)
        .addValue("source", source);

    int updated = jdbcTemplate.update("""
        UPDATE canonical_promo_product
        SET participation_status = :newStatus,
            participation_decision_source = :source,
            updated_at = NOW()
        WHERE marketplace_offer_id = :offerId
          AND canonical_promo_campaign_id = :campaignId
          AND participation_status = :expectedStatus
        """, params);

    if (updated == 0) {
      log.warn("CAS conflict updating canonical_promo_product after action: actionId={}",
          action.getId());
    }
  }

  private void recordAttempt(PromoActionEntity action, int attemptNumber,
      PromoAttemptOutcome outcome, String errorMessage,
      String requestSummary, String responseSummary) {
    var attempt = new PromoActionAttemptEntity();
    attempt.setPromoActionId(action.getId());
    attempt.setAttemptNumber(attemptNumber);
    attempt.setStartedAt(OffsetDateTime.now());
    attempt.setCompletedAt(OffsetDateTime.now());
    attempt.setOutcome(outcome);
    attempt.setErrorMessage(errorMessage);
    attempt.setProviderRequestSummary(requestSummary);
    attempt.setProviderResponseSummary(responseSummary);
    attemptRepository.save(attempt);
  }

  private ActionContext loadActionContext(PromoActionEntity action) {
    var params = new MapSqlParameterSource()
        .addValue("campaignId", action.getCanonicalPromoCampaignId())
        .addValue("offerId", action.getMarketplaceOfferId());

    try {
      CampaignOfferRow row = jdbcTemplate.query("""
          SELECT cpc.external_promo_id, cpc.connection_id,
                 mo.marketplace_sku
          FROM canonical_promo_campaign cpc
          JOIN canonical_promo_product cpp
              ON cpp.canonical_promo_campaign_id = cpc.id
              AND cpp.marketplace_offer_id = :offerId
          JOIN marketplace_offer mo ON mo.id = :offerId
          WHERE cpc.id = :campaignId
          """, params, rs -> {
        if (!rs.next()) {
          return null;
        }
        return new CampaignOfferRow(
            Long.parseLong(rs.getString("external_promo_id")),
            Long.parseLong(rs.getString("marketplace_sku")),
            rs.getLong("connection_id"));
      });

      if (row == null) {
        return null;
      }

      var creds = credentialResolver.resolve(row.connectionId());

      return new ActionContext(
          row.externalPromoId(),
          row.externalProductId(),
          row.connectionId(),
          creds.ozonClientId(),
          creds.ozonApiKey());
    } catch (Exception e) {
      log.error("Failed to load action context: actionId={}, campaignId={}, offerId={}, error={}",
          action.getId(), action.getCanonicalPromoCampaignId(),
          action.getMarketplaceOfferId(), e.getMessage(), e);
      return null;
    }
  }

  private record CampaignOfferRow(long externalPromoId, long externalProductId,
                                  long connectionId) {

  }

  private String buildRequestSummary(PromoActionEntity action, ActionContext ctx) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          "action_type", action.getActionType().name(),
          "external_action_id", ctx.externalActionId(),
          "external_product_id", ctx.externalProductId(),
          "target_promo_price", action.getTargetPromoPrice() != null
              ? action.getTargetPromoPrice().toString() : "null"));
    } catch (Exception e) {
      log.warn("Failed to serialize request summary: actionId={}, error={}",
          action.getId(), e.getMessage(), e);
      return "{}";
    }
  }

  private boolean isRetryable(Exception e) {
    String msg = e.getMessage();
    if (msg == null) {
      return false;
    }
    return msg.contains("429") || msg.contains("503") || msg.contains("timeout")
        || msg.contains("connect");
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private record ActionContext(long externalActionId, long externalProductId,
                               long connectionId, String clientId, String apiKey) {

  }

  @Transactional(readOnly = true)
  public Page<PromoActionResponse> listActions(long workspaceId, Long campaignId,
      PromoActionStatus status, PromoActionType actionType,
      Pageable pageable) {
    boolean hasMultipleFilters = (campaignId != null ? 1 : 0)
        + (status != null ? 1 : 0) + (actionType != null ? 1 : 0) > 1;

    if (hasMultipleFilters || actionType != null) {
      return actionQueryRepository.findFiltered(
              workspaceId, campaignId, status, actionType, pageable)
          .map(actionMapper::toResponse);
    }

    Page<PromoActionEntity> page;
    if (campaignId != null) {
      page = actionRepository.findAllByWorkspaceIdAndCanonicalPromoCampaignId(
          workspaceId, campaignId, pageable);
    } else if (status != null) {
      page = actionRepository.findAllByWorkspaceIdAndStatus(workspaceId, status, pageable);
    } else {
      page = actionRepository.findAllByWorkspaceId(workspaceId, pageable);
    }
    return page.map(actionMapper::toResponse);
  }

  @Transactional
  public void approveAction(long actionId, long workspaceId) {
    PromoActionEntity action = findActionOrThrow(actionId, workspaceId);

    if (action.getStatus() != PromoActionStatus.PENDING_APPROVAL) {
      throw BadRequestException.of(MessageCodes.PROMO_ACTION_INVALID_TRANSITION,
          action.getStatus().name(), "APPROVED", actionId);
    }

    int updated = actionRepository.casUpdateStatus(
        actionId, PromoActionStatus.PENDING_APPROVAL, PromoActionStatus.APPROVED);

    if (updated == 0) {
      publishAudit("promo_action.approve", actionId, workspaceId, "CAS_CONFLICT");
      throw ConflictException.of(MessageCodes.PROMO_ACTION_CAS_CONFLICT, actionId);
    }

    publishAudit("promo_action.approve", actionId, workspaceId, "SUCCESS");
    log.info("Promo action approved: actionId={}", actionId);
  }

  @Transactional
  public void rejectAction(long actionId, String reason, long workspaceId) {
    PromoActionEntity action = findActionOrThrow(actionId, workspaceId);

    if (action.getStatus() != PromoActionStatus.PENDING_APPROVAL) {
      throw BadRequestException.of(MessageCodes.PROMO_ACTION_INVALID_TRANSITION,
          action.getStatus().name(), "CANCELLED", actionId);
    }

    int updated = actionRepository.casUpdateStatus(
        actionId, PromoActionStatus.PENDING_APPROVAL, PromoActionStatus.CANCELLED);

    if (updated == 0) {
      throw ConflictException.of(MessageCodes.PROMO_ACTION_CAS_CONFLICT, actionId);
    }

    action.setStatus(PromoActionStatus.CANCELLED);
    action.setCancelReason(reason);
    actionRepository.save(action);

    publishAudit("promo_action.reject", actionId, workspaceId, "SUCCESS");
    log.info("Promo action rejected: actionId={}, reason={}", actionId, reason);
  }

  @Transactional
  public void cancelAction(long actionId, String cancelReason, long workspaceId) {
    PromoActionEntity action = findActionOrThrow(actionId, workspaceId);

    if (!action.getStatus().isCancellable()) {
      throw BadRequestException.of(MessageCodes.PROMO_ACTION_NOT_CANCELLABLE,
          actionId, action.getStatus().name());
    }

    int updated = actionRepository.casUpdateStatus(
        actionId, action.getStatus(), PromoActionStatus.CANCELLED);

    if (updated == 0) {
      throw ConflictException.of(MessageCodes.PROMO_ACTION_CAS_CONFLICT, actionId);
    }

    action.setStatus(PromoActionStatus.CANCELLED);
    action.setCancelReason(cancelReason);
    actionRepository.save(action);

    publishAudit("promo_action.cancel", actionId, workspaceId, "SUCCESS");
    log.info("Promo action cancelled: actionId={}, reason={}", actionId, cancelReason);
  }

  @Transactional
  public BulkPromoActionResponse bulkApprove(BulkPromoActionRequest request, long workspaceId) {
    List<PromoActionEntity> actions = actionRepository.findAllByIdInAndWorkspaceId(
        request.actionIds(), workspaceId);

    List<Long> succeeded = new ArrayList<>();
    List<BulkPromoActionResponse.FailedItem> failed = new ArrayList<>();

    for (PromoActionEntity action : actions) {
      if (action.getStatus() != PromoActionStatus.PENDING_APPROVAL) {
        failed.add(new BulkPromoActionResponse.FailedItem(
            action.getId(), MessageCodes.PROMO_ACTION_INVALID_TRANSITION));
        continue;
      }

      int updated = actionRepository.casUpdateStatus(
          action.getId(), PromoActionStatus.PENDING_APPROVAL, PromoActionStatus.APPROVED);

      if (updated > 0) {
        succeeded.add(action.getId());
      } else {
        failed.add(new BulkPromoActionResponse.FailedItem(
            action.getId(), MessageCodes.PROMO_ACTION_CAS_CONFLICT));
      }
    }

    log.info("Bulk approve promo actions: succeeded={}, failed={}", succeeded.size(),
        failed.size());
    return new BulkPromoActionResponse(succeeded, failed);
  }

  @Transactional
  public BulkPromoActionResponse bulkReject(BulkPromoActionRequest request, String reason,
      long workspaceId) {
    List<PromoActionEntity> actions = actionRepository.findAllByIdInAndWorkspaceId(
        request.actionIds(), workspaceId);

    List<Long> succeeded = new ArrayList<>();
    List<BulkPromoActionResponse.FailedItem> failed = new ArrayList<>();

    for (PromoActionEntity action : actions) {
      if (action.getStatus() != PromoActionStatus.PENDING_APPROVAL) {
        failed.add(new BulkPromoActionResponse.FailedItem(
            action.getId(), MessageCodes.PROMO_ACTION_INVALID_TRANSITION));
        continue;
      }

      int updated = actionRepository.casUpdateStatus(
          action.getId(), PromoActionStatus.PENDING_APPROVAL, PromoActionStatus.CANCELLED);

      if (updated > 0) {
        action.setStatus(PromoActionStatus.CANCELLED);
        action.setCancelReason(reason);
        actionRepository.save(action);
        succeeded.add(action.getId());
      } else {
        failed.add(new BulkPromoActionResponse.FailedItem(
            action.getId(), MessageCodes.PROMO_ACTION_CAS_CONFLICT));
      }
    }

    log.info("Bulk reject promo actions: succeeded={}, failed={}", succeeded.size(), failed.size());
    return new BulkPromoActionResponse(succeeded, failed);
  }

  private PromoActionEntity findActionOrThrow(long actionId, long workspaceId) {
    return actionRepository.findByIdAndWorkspaceId(actionId, workspaceId)
        .orElseThrow(() -> NotFoundException.entity("PromoAction", actionId));
  }

  private void publishAudit(String actionType, long actionId, long workspaceId, String outcome) {
    Long userId = null;
    try {
      userId = workspaceContext.getUserId();
    } catch (Exception e) {
      log.warn("Failed to resolve userId for audit: actionId={}, error={}",
          actionId, e.getMessage());
    }
    try {
      eventPublisher.publishEvent(new AuditEvent(
          workspaceId, "USER", userId, actionType,
          ENTITY_TYPE, String.valueOf(actionId),
          outcome, null, null, null));
    } catch (Exception e) {
      log.error("Failed to publish audit event: actionType={}, actionId={}, error={}",
          actionType, actionId, e.getMessage(), e);
    }
  }
}
