package io.datapulse.core.repository.account.invite;

import io.datapulse.core.entity.account.invite.AccountInviteEntity;
import io.datapulse.domain.AccountInviteStatus;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountInviteRepository extends JpaRepository<AccountInviteEntity, Long> {

  Optional<AccountInviteEntity> findByTokenHash(String tokenHash);

  boolean existsByEmailAndStatusAndExpiresAtAfter(String email, AccountInviteStatus status,
      OffsetDateTime now);
}
