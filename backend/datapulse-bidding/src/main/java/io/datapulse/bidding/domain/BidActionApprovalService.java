package io.datapulse.bidding.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.datapulse.bidding.persistence.BidActionEntity;
import io.datapulse.bidding.persistence.BidActionRepository;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import io.datapulse.common.exception.NotFoundException;
import io.datapulse.platform.outbox.OutboxEventType;
import io.datapulse.platform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidActionApprovalService {

  private final BidActionRepository actionRepository;
  private final OutboxService outboxService;

  @Transactional
  public void approve(long actionId) {
    BidActionEntity action = findOrThrow(actionId);
    requireStatus(action, BidActionStatus.PENDING_APPROVAL);

    action.setStatus(BidActionStatus.APPROVED);
    action.setApprovedAt(OffsetDateTime.now());
    actionRepository.save(action);

    outboxService.createEvent(
        OutboxEventType.BID_ACTION_EXECUTE,
        "bid_action",
        action.getId(),
        Map.of("bidActionId", action.getId()));

    log.info("Bid action approved: actionId={}, offerId={}",
        actionId, action.getMarketplaceOfferId());
  }

  @Transactional
  public void reject(long actionId) {
    BidActionEntity action = findOrThrow(actionId);
    requireStatus(action, BidActionStatus.PENDING_APPROVAL);

    action.setStatus(BidActionStatus.CANCELLED);
    actionRepository.save(action);

    log.info("Bid action rejected: actionId={}, offerId={}",
        actionId, action.getMarketplaceOfferId());
  }

  @Transactional
  public void bulkApprove(List<Long> actionIds) {
    for (long actionId : actionIds) {
      approve(actionId);
    }
  }

  @Transactional
  public void bulkReject(List<Long> actionIds) {
    for (long actionId : actionIds) {
      reject(actionId);
    }
  }

  private BidActionEntity findOrThrow(long actionId) {
    return actionRepository.findById(actionId)
        .orElseThrow(() -> NotFoundException.of(
            MessageCodes.BIDDING_ACTION_NOT_FOUND));
  }

  private void requireStatus(BidActionEntity action, BidActionStatus expected) {
    if (action.getStatus() != expected) {
      throw BadRequestException.of(
          MessageCodes.BIDDING_ACTION_INVALID_STATE);
    }
  }
}
