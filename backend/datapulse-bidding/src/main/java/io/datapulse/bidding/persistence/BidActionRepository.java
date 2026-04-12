package io.datapulse.bidding.persistence;

import io.datapulse.bidding.domain.BidActionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BidActionRepository extends JpaRepository<BidActionEntity, Long> {

  Page<BidActionEntity> findByWorkspaceIdAndStatus(
      long workspaceId, BidActionStatus status, Pageable pageable);

  List<BidActionEntity> findByMarketplaceOfferIdAndStatusIn(
      long marketplaceOfferId, List<BidActionStatus> statuses);

  List<BidActionEntity> findByBidDecisionIdIn(List<Long> decisionIds);
}
