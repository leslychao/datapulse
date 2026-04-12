package io.datapulse.bidding.persistence;

import java.time.OffsetDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.datapulse.bidding.domain.BiddingRunStatus;

public interface BiddingRunRepository extends JpaRepository<BiddingRunEntity, Long> {

  Page<BiddingRunEntity> findByBidPolicyId(Long bidPolicyId, Pageable pageable);

  boolean existsByBidPolicyIdAndStatus(Long bidPolicyId, BiddingRunStatus status);

  Page<BiddingRunEntity> findByWorkspaceId(Long workspaceId, Pageable pageable);

  @Query("""
      SELECT COUNT(r) FROM BiddingRunEntity r
      WHERE r.bidPolicyId = :policyId
        AND r.status = :status
        AND r.completedAt >= :since
      """)
  long countByPolicyIdAndStatusSince(
      @Param("policyId") long policyId,
      @Param("status") BiddingRunStatus status,
      @Param("since") OffsetDateTime since);
}
