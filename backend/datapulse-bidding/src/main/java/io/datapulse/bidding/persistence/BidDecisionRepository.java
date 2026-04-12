package io.datapulse.bidding.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BidDecisionRepository extends JpaRepository<BidDecisionEntity, Long> {

  List<BidDecisionEntity> findByBiddingRunId(Long biddingRunId);

  Optional<BidDecisionEntity> findFirstByWorkspaceIdAndMarketplaceOfferIdOrderByCreatedAtDesc(
      Long workspaceId, Long marketplaceOfferId);

  Page<BidDecisionEntity> findByWorkspaceId(Long workspaceId, Pageable pageable);

  Page<BidDecisionEntity> findByWorkspaceIdAndBidPolicyId(
      Long workspaceId, Long bidPolicyId, Pageable pageable);

  Page<BidDecisionEntity> findByWorkspaceIdAndBiddingRunId(
      Long workspaceId, Long biddingRunId, Pageable pageable);

  Page<BidDecisionEntity> findByWorkspaceIdAndMarketplaceOfferId(
      Long workspaceId, Long marketplaceOfferId, Pageable pageable);

  @Query("""
      SELECT COUNT(d) FROM BidDecisionEntity d
      WHERE d.marketplaceOfferId = :offerId
        AND d.createdAt >= CURRENT_TIMESTAMP - :periodDays * INTERVAL '1 day'
        AND d.decisionType IN (
            io.datapulse.bidding.domain.BidDecisionType.BID_UP,
            io.datapulse.bidding.domain.BidDecisionType.BID_DOWN)
      """)
  int countDirectionChanges(
      @Param("offerId") long marketplaceOfferId,
      @Param("periodDays") int periodDays);
}
