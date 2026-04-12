package io.datapulse.bidding.domain;

import io.datapulse.bidding.persistence.BidPolicyAssignmentEntity;
import io.datapulse.bidding.persistence.BidPolicyAssignmentRepository;
import io.datapulse.common.error.MessageCodes;
import io.datapulse.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BidPolicyAssignmentService {

  private final BidPolicyAssignmentRepository assignmentRepository;

  @Transactional
  public BidPolicyAssignmentEntity assign(
      long bidPolicyId,
      long workspaceId,
      Long marketplaceOfferId,
      String campaignExternalId,
      AssignmentScope scope) {

    if (marketplaceOfferId != null
        && assignmentRepository.existsByMarketplaceOfferId(marketplaceOfferId)) {
      throw BadRequestException.of(MessageCodes.BIDDING_ASSIGNMENT_CONFLICT);
    }

    var entity = new BidPolicyAssignmentEntity();
    entity.setBidPolicyId(bidPolicyId);
    entity.setWorkspaceId(workspaceId);
    entity.setMarketplaceOfferId(marketplaceOfferId);
    entity.setCampaignExternalId(campaignExternalId);
    entity.setAssignmentScope(scope);

    return assignmentRepository.save(entity);
  }

  @Transactional
  public void unassign(long assignmentId) {
    assignmentRepository.deleteById(assignmentId);
  }

  @Transactional
  public List<BidPolicyAssignmentEntity> bulkAssign(
      long bidPolicyId,
      long workspaceId,
      List<Long> marketplaceOfferIds,
      AssignmentScope scope) {

    List<BidPolicyAssignmentEntity> results = new ArrayList<>(marketplaceOfferIds.size());
    for (Long offerId : marketplaceOfferIds) {
      results.add(assign(bidPolicyId, workspaceId, offerId, null, scope));
    }
    return results;
  }

  @Transactional(readOnly = true)
  public Page<BidPolicyAssignmentEntity> listAssignments(long bidPolicyId, Pageable pageable) {
    return assignmentRepository.findByBidPolicyId(bidPolicyId, pageable);
  }
}
