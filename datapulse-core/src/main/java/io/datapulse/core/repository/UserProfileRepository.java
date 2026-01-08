package io.datapulse.core.repository;

import io.datapulse.core.entity.UserProfileEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, Long> {

  Optional<UserProfileEntity> findByKeycloakSub(String keycloakSub);

  boolean existsByKeycloakSub(String keycloakSub);

  boolean existsByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
