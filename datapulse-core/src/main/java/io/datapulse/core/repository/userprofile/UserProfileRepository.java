package io.datapulse.core.repository.userprofile;

import io.datapulse.core.entity.userprofile.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository
    extends JpaRepository<UserProfileEntity, Long>, UserProfileUpsertRepository {

  boolean existsByKeycloakSub(String keycloakSub);

  boolean existsByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
