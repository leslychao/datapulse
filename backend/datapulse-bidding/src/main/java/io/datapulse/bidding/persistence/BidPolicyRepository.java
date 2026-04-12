package io.datapulse.bidding.persistence;

import io.datapulse.bidding.domain.BidPolicyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BidPolicyRepository extends JpaRepository<BidPolicyEntity, Long> {

  List<BidPolicyEntity> findByWorkspaceIdAndStatus(Long workspaceId, BidPolicyStatus status);

  Page<BidPolicyEntity> findByWorkspaceId(Long workspaceId, Pageable pageable);
}
