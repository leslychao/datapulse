package io.datapulse.bidding.persistence;

import io.datapulse.bidding.domain.BidPolicyStatus;
import io.datapulse.bidding.domain.BiddingStrategyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BidPolicyRepository extends JpaRepository<BidPolicyEntity, Long> {

  List<BidPolicyEntity> findByWorkspaceIdAndStatus(Long workspaceId, BidPolicyStatus status);

  Optional<BidPolicyEntity> findFirstByWorkspaceIdAndStrategyTypeAndStatus(
      Long workspaceId, BiddingStrategyType strategyType, BidPolicyStatus status);

  Page<BidPolicyEntity> findByWorkspaceId(Long workspaceId, Pageable pageable);

  List<BidPolicyEntity> findByStatus(BidPolicyStatus status);

  @Query("""
      SELECT DISTINCT p.workspaceId FROM BidPolicyEntity p
      WHERE p.status = 'ACTIVE'
      """)
  List<Long> findDistinctWorkspaceIdsWithActivePolicies();
}
