package io.datapulse.core.repository.account.invite;

import io.datapulse.core.entity.account.invite.AccountInviteAcceptanceEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountInviteAcceptanceRepository extends JpaRepository<AccountInviteAcceptanceEntity, Long> {

  Optional<AccountInviteAcceptanceEntity> findByInviteId(long inviteId);
}
