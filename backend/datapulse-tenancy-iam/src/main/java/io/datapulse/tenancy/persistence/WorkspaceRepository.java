package io.datapulse.tenancy.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, Long> {

    Optional<WorkspaceEntity> findByTenant_IdAndSlug(Long tenantId, String slug);

    boolean existsByTenant_IdAndSlug(Long tenantId, String slug);
}
