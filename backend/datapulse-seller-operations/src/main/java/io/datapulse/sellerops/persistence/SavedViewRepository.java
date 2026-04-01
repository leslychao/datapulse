package io.datapulse.sellerops.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedViewRepository extends JpaRepository<SavedViewEntity, Long> {

    List<SavedViewEntity> findByWorkspaceIdAndUserIdOrderByCreatedAtAsc(Long workspaceId, Long userId);

    Optional<SavedViewEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByWorkspaceIdAndUserIdAndName(Long workspaceId, Long userId, String name);
}
