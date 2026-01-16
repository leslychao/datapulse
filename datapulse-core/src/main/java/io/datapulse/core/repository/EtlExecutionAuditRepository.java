package io.datapulse.core.repository;

import io.datapulse.core.entity.EtlExecutionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EtlExecutionAuditRepository extends JpaRepository<EtlExecutionAuditEntity, Long> {

  @Modifying
  @Query("delete from EtlExecutionAuditEntity e where e.id = :id")
  int deleteByIdAndIdIsNotNull(@Param("id") Long id);
}
