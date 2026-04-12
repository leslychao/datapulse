package io.datapulse.bidding.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BiddingRunRepository extends JpaRepository<BiddingRunEntity, Long> {

  Page<BiddingRunEntity> findByBidPolicyId(Long bidPolicyId, Pageable pageable);

  Page<BiddingRunEntity> findByWorkspaceId(Long workspaceId, Pageable pageable);
}
