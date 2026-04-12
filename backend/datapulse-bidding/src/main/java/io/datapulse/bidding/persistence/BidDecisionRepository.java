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

  @Query(value = """
      SELECT COUNT(*) FROM bid_decision
      WHERE marketplace_offer_id = :offerId
        AND created_at >= now() - make_interval(days => :periodDays)
        AND decision_type IN ('BID_UP', 'BID_DOWN')
      """, nativeQuery = true)
  int countDirectionChanges(
      @Param("offerId") long marketplaceOfferId,
      @Param("periodDays") int periodDays);
}
