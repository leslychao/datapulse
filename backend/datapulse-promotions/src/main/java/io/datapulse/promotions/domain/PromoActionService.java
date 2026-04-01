package io.datapulse.promotions.domain;

import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.ConflictException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.promotions.api.BulkPromoActionRequest;
import io.datapulse.promotions.api.BulkPromoActionResponse;
import io.datapulse.promotions.api.PromoActionMapper;
import io.datapulse.promotions.api.PromoActionResponse;
import io.datapulse.promotions.persistence.PromoActionEntity;
import io.datapulse.promotions.persistence.PromoActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoActionService {

    private final PromoActionRepository actionRepository;
    private final PromoActionMapper actionMapper;

    @Transactional(readOnly = true)
    public Page<PromoActionResponse> listActions(long workspaceId, Long campaignId,
                                                  PromoActionStatus status, Pageable pageable) {
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
            throw ConflictException.of(MessageCodes.PROMO_ACTION_CAS_CONFLICT, actionId);
        }

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
}
