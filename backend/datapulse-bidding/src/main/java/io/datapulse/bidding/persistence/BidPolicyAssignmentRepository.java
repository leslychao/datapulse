package io.datapulse.bidding.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidPolicyAssignmentRepository
    extends JpaRepository<BidPolicyAssignmentEntity, Long> {

  List<BidPolicyAssignmentEntity> findByBidPolicyId(Long bidPolicyId);

  Page<BidPolicyAssignmentEntity> findByBidPolicyId(Long bidPolicyId, Pageable pageable);

  Optional<BidPolicyAssignmentEntity> findByMarketplaceOfferId(Long marketplaceOfferId);

  boolean existsByMarketplaceOfferId(Long marketplaceOfferId);

  int countByBidPolicyId(Long bidPolicyId);

  void deleteByBidPolicyIdAndMarketplaceOfferId(Long bidPolicyId, Long marketplaceOfferId);
}
