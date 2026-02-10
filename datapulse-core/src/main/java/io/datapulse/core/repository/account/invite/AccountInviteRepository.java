package io.datapulse.core.repository.account.invite;

import io.datapulse.core.entity.account.invite.AccountInviteEntity;
import io.datapulse.domain.AccountInviteStatus;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AccountInviteRepository extends JpaRepository<AccountInviteEntity, Long> {

  Optional<AccountInviteEntity> findByTokenHash(String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select i
      from AccountInviteEntity i
      where i.tokenHash = :tokenHash
      """)
  Optional<AccountInviteEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  boolean existsByEmailAndStatusAndExpiresAtAfter(String email, AccountInviteStatus status,
      OffsetDateTime now);
}
