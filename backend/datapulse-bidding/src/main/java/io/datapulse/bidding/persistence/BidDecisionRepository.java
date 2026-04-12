package io.datapulse.bidding.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidDecisionRepository extends JpaRepository<BidDecisionEntity, Long> {

  List<BidDecisionEntity> findByBiddingRunId(Long biddingRunId);

  Optional<BidDecisionEntity> findFirstByWorkspaceIdAndMarketplaceOfferIdOrderByCreatedAtDesc(
      Long workspaceId, Long marketplaceOfferId);
}
