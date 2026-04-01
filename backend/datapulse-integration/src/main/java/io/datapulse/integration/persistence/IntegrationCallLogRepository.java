package io.datapulse.integration.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface IntegrationCallLogRepository extends JpaRepository<IntegrationCallLogEntity, Long> {

    @Query("""
            SELECT l FROM IntegrationCallLogEntity l
            WHERE l.marketplaceConnectionId = :connectionId
              AND (CAST(:from AS TIMESTAMP) IS NULL OR l.createdAt >= :from)
              AND (CAST(:to AS TIMESTAMP) IS NULL OR l.createdAt <= :to)
              AND (CAST(:endpoint AS STRING) IS NULL OR l.endpoint LIKE CONCAT('%', CAST(:endpoint AS STRING), '%'))
              AND (CAST(:httpStatus AS INTEGER) IS NULL OR l.httpStatus = :httpStatus)
            ORDER BY l.createdAt DESC
            """)
    Page<IntegrationCallLogEntity> findByFilters(
            @Param("connectionId") Long connectionId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("endpoint") String endpoint,
            @Param("httpStatus") Integer httpStatus,
            Pageable pageable);
}
