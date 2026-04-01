package io.datapulse.promotions.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.ConflictException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.platform.audit.AuditEvent;
import io.datapulse.platform.security.WorkspaceContext;
import io.datapulse.promotions.api.BulkPromoActionRequest;
import io.datapulse.promotions.api.BulkPromoActionResponse;
import io.datapulse.promotions.api.PromoActionMapper;
import io.datapulse.promotions.api.PromoActionResponse;
import io.datapulse.promotions.persistence.PromoActionEntity;
import io.datapulse.promotions.persistence.PromoActionQueryRepository;
import io.datapulse.promotions.persistence.PromoActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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

        log.info("Bulk approve promo actions: succeeded={}, failed={}", succeeded.size(), failed.size());
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
        } catch (Exception ignored) {
        }
        eventPublisher.publishEvent(new AuditEvent(
                workspaceId, "USER", userId, actionType,
                ENTITY_TYPE, String.valueOf(actionId),
                outcome, null, null, null));
    }
}
