package io.datapulse.integration.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SecretReferenceRepository extends JpaRepository<SecretReferenceEntity, Long> {
}
