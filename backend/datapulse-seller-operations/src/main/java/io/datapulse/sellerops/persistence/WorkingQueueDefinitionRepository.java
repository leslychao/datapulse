package io.datapulse.sellerops.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkingQueueDefinitionRepository extends JpaRepository<WorkingQueueDefinitionEntity, Long> {

    List<WorkingQueueDefinitionEntity> findByWorkspaceIdOrderByCreatedAtAsc(long workspaceId);

    List<WorkingQueueDefinitionEntity> findByWorkspaceIdAndEnabledTrueAndAutoCriteriaIsNotNull(long workspaceId);

    Optional<WorkingQueueDefinitionEntity> findByIdAndWorkspaceId(long id, long workspaceId);

    boolean existsByWorkspaceIdAndName(long workspaceId, String name);

    long countByWorkspaceIdAndSystemFalse(long workspaceId);

    @Query("""
            SELECT wqd FROM WorkingQueueDefinitionEntity wqd
            WHERE wqd.enabled = true AND wqd.autoCriteria IS NOT NULL
            """)
    List<WorkingQueueDefinitionEntity> findAllEnabledWithAutoCriteria();
}
