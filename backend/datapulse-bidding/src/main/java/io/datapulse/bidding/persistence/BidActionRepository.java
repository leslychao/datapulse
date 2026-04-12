package io.datapulse.bidding.persistence;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.datapulse.bidding.domain.BidActionStatus;

public interface BidActionRepository extends JpaRepository<BidActionEntity, Long> {

  Page<BidActionEntity> findByWorkspaceIdAndStatus(
      long workspaceId, BidActionStatus status, Pageable pageable);

  List<BidActionEntity> findByMarketplaceOfferIdAndStatusIn(
      long marketplaceOfferId, List<BidActionStatus> statuses);

  List<BidActionEntity> findByBidDecisionIdIn(List<Long> decisionIds);

  @Query("""
      SELECT COUNT(a) FROM BidActionEntity a, BidDecisionEntity d
      WHERE a.bidDecisionId = d.id
        AND d.bidPolicyId = :policyId
        AND a.status = :status
        AND a.createdAt >= :since
      """)
  long countByStatusAndPolicySince(
      @Param("policyId") long policyId,
      @Param("status") BidActionStatus status,
      @Param("since") OffsetDateTime since);
}
