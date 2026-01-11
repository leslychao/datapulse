package io.datapulse.core.service.account;

import io.datapulse.core.entity.UserProfileEntity;
import io.datapulse.core.repository.UserProfileRepository;
import io.datapulse.domain.dto.security.AuthenticatedUser;
import jakarta.transaction.Transactional;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProfileProvisioningService {

  private final UserProfileRepository repository;

  @Transactional
  public void ensureUserProfile(AuthenticatedUser identity) {
    repository.findByKeycloakSub(identity.userId())
        .map(existing -> updateFromIdentity(existing, identity))
        .orElseGet(() -> createFromIdentity(identity));
  }

  private UserProfileEntity updateFromIdentity(
      UserProfileEntity entity,
      AuthenticatedUser identity
  ) {
    boolean changed = false;

    if (!Objects.equals(entity.getEmail(), identity.email())) {
      entity.setEmail(identity.email());
      changed = true;
    }

    if (hasText(identity.fullName())
        && !Objects.equals(entity.getFullName(), identity.fullName())) {
      entity.setFullName(identity.fullName());
      changed = true;
    }

    if (hasText(identity.username())
        && !Objects.equals(entity.getUsername(), identity.username())) {
      entity.setUsername(identity.username());
      changed = true;
    }

    return changed ? repository.save(entity) : entity;
  }

  private UserProfileEntity createFromIdentity(AuthenticatedUser identity) {
    UserProfileEntity entity = new UserProfileEntity();
    entity.setKeycloakSub(identity.userId());
    entity.setEmail(identity.email());
    entity.setFullName(identity.fullName());
    entity.setUsername(identity.username());

    try {
      return repository.save(entity);
    } catch (DataIntegrityViolationException ex) {
      return repository.findByKeycloakSub(identity.userId())
          .orElseThrow(() -> new IllegalStateException(
              "UserProfile provisioning race detected for keycloakSub=" + identity.userId(),
              ex
          ));
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
