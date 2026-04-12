package io.datapulse.bidding.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidPolicyAssignmentRepository
    extends JpaRepository<BidPolicyAssignmentEntity, Long> {

  List<BidPolicyAssignmentEntity> findByBidPolicyId(Long bidPolicyId);

  Optional<BidPolicyAssignmentEntity> findByMarketplaceOfferId(Long marketplaceOfferId);

  boolean existsByMarketplaceOfferId(Long marketplaceOfferId);

  void deleteByBidPolicyIdAndMarketplaceOfferId(Long bidPolicyId, Long marketplaceOfferId);
}
