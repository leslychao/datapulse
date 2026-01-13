package io.datapulse.core.repository;

import io.datapulse.core.entity.EtlExecutionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtlExecutionAuditRepository
    extends JpaRepository<EtlExecutionAuditEntity, Long> {

}
