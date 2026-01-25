package io.datapulse.core.repository.account.invite;

import io.datapulse.core.entity.account.invite.AccountInviteTargetEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountInviteTargetRepository extends
    JpaRepository<AccountInviteTargetEntity, Long> {

  List<AccountInviteTargetEntity> findAllByInviteId(long inviteId);
}
