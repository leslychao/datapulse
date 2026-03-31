package io.datapulse.tenancy.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

    Optional<AppUserEntity> findByExternalId(String externalId);

    Optional<AppUserEntity> findByEmail(String email);

    boolean existsByExternalId(String externalId);
}
