package io.datapulse.tenancy.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<TenantEntity, Long> {

    Optional<TenantEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByOwnerUserId(Long ownerUserId);
}
