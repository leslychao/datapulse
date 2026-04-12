package io.datapulse.bidding.domain;

import io.datapulse.bidding.persistence.BidDecisionEntity;
import io.datapulse.bidding.persistence.BidDecisionRepository;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BidDecisionQueryService {

  private final BidDecisionRepository decisionRepository;

  @Transactional(readOnly = true)
  public Page<BidDecisionEntity> listDecisions(
      Long workspaceId,
      Long bidPolicyId,
      Long biddingRunId,
      Long marketplaceOfferId,
      Pageable pageable) {

    if (biddingRunId != null) {
      return decisionRepository.findByWorkspaceIdAndBiddingRunId(
          workspaceId, biddingRunId, pageable);
    }
    if (marketplaceOfferId != null) {
      return decisionRepository.findByWorkspaceIdAndMarketplaceOfferId(
          workspaceId, marketplaceOfferId, pageable);
    }
    if (bidPolicyId != null) {
      return decisionRepository.findByWorkspaceIdAndBidPolicyId(
          workspaceId, bidPolicyId, pageable);
    }
    return decisionRepository.findByWorkspaceId(workspaceId, pageable);
  }

  @Transactional(readOnly = true)
  public BidDecisionEntity getDecision(long id) {
    return decisionRepository.findById(id)
        .orElseThrow(() -> NotFoundException.entity("BidDecision", id));
  }
}
