package io.datapulse.core.repository;

import io.datapulse.core.entity.EtlSyncAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtlSyncAuditRepository extends JpaRepository<EtlSyncAuditEntity, Long> {

}
