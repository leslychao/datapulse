package io.datapulse.sellerops.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkingQueueAssignmentRepository extends JpaRepository<WorkingQueueAssignmentEntity, Long> {

    Page<WorkingQueueAssignmentEntity> findByQueueDefinitionIdAndStatusIn(
            long queueDefinitionId, List<String> statuses, Pageable pageable);

    Page<WorkingQueueAssignmentEntity> findByQueueDefinitionId(
            long queueDefinitionId, Pageable pageable);

    Page<WorkingQueueAssignmentEntity> findByQueueDefinitionIdAndAssignedToUserId(
            long queueDefinitionId, long assignedToUserId, Pageable pageable);

    Page<WorkingQueueAssignmentEntity> findByQueueDefinitionIdAndStatusInAndAssignedToUserId(
            long queueDefinitionId, List<String> statuses, long assignedToUserId,
            Pageable pageable);

    Optional<WorkingQueueAssignmentEntity> findByIdAndQueueDefinitionId(long id, long queueDefinitionId);

    @Query("""
            SELECT COUNT(a) FROM WorkingQueueAssignmentEntity a
            WHERE a.queueDefinitionId = :queueId AND a.status = :status
            """)
    long countByQueueAndStatus(@Param("queueId") long queueDefinitionId,
                               @Param("status") String status);

    @Query("""
            SELECT COUNT(a) FROM WorkingQueueAssignmentEntity a
            WHERE a.queueDefinitionId = :queueId AND a.status NOT IN ('DONE', 'DISMISSED')
            """)
    long countActiveByQueue(@Param("queueId") long queueDefinitionId);

    @Query("""
            SELECT a FROM WorkingQueueAssignmentEntity a
            WHERE a.queueDefinitionId = :queueId
              AND a.entityType = :entityType
              AND a.entityId = :entityId
              AND a.status NOT IN ('DONE', 'DISMISSED')
            """)
    Optional<WorkingQueueAssignmentEntity> findActiveAssignment(
            @Param("queueId") long queueDefinitionId,
            @Param("entityType") String entityType,
            @Param("entityId") long entityId);

    @Query("""
            SELECT a FROM WorkingQueueAssignmentEntity a
            WHERE a.queueDefinitionId = :queueId
              AND a.status NOT IN ('DONE', 'DISMISSED')
            """)
    List<WorkingQueueAssignmentEntity> findAllActiveByQueue(@Param("queueId") long queueDefinitionId);
}
