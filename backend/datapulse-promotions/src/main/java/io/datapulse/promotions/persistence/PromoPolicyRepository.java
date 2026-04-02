package io.datapulse.promotions.persistence;

import io.datapulse.promotions.domain.PromoPolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromoPolicyRepository extends JpaRepository<PromoPolicyEntity, Long> {

    List<PromoPolicyEntity> findAllByWorkspaceId(Long workspaceId);

    List<PromoPolicyEntity> findAllByWorkspaceIdAndStatus(Long workspaceId, PromoPolicyStatus status);

    List<PromoPolicyEntity> findAllByWorkspaceIdAndStatusIn(Long workspaceId, List<PromoPolicyStatus> statuses);

    Optional<PromoPolicyEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByWorkspaceIdAndStatus(Long workspaceId, PromoPolicyStatus status);
}
