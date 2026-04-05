package io.datapulse.sellerops.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SavedViewRepository extends JpaRepository<SavedViewEntity, Long> {

    List<SavedViewEntity> findByWorkspaceIdAndUserIdOrderByCreatedAtAsc(Long workspaceId, Long userId);

    @Query("""
            SELECT sv FROM SavedViewEntity sv
            WHERE sv.workspaceId = :workspaceId
              AND (sv.userId = :userId OR sv.isSystem = true)
            ORDER BY sv.isSystem DESC, sv.createdAt ASC
            """)
    List<SavedViewEntity> findByWorkspaceIdIncludingSystem(
            @Param("workspaceId") Long workspaceId,
            @Param("userId") Long userId);

    Optional<SavedViewEntity> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByWorkspaceIdAndUserIdAndName(Long workspaceId, Long userId, String name);

    long countByWorkspaceIdAndUserId(Long workspaceId, Long userId);
}
